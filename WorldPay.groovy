def call(String serviceName) {

    def serviceBucket = "gw2-${serviceName}-pipelines"
    def appPlayBook = "${serviceName}-ami-role"
    def jarName = "${serviceName}-assembly-0.1-SNAPSHOT.jar"
    def terraformServiceName = "${serviceName}"
    def dnsName = "${serviceName}"
    def amiIdentifier = "${serviceName}"

    def ECR_REGIONS = ['eu-west-1', 'eu-west-2', 'eu-central-1']
//  TODO: add region at the end of vault_role like: jenkinsWithEnvForAnsible-${region}
    def vault_role = "jenkinsWithEnvForAnsible-eu-west-1"

    def appHash = "NotSet"
    def infHash = "NotSet"

    def destroy = ""

    def fqdnMap = [
            npe     :'npe.access.worldpay.com',
            preprod :'preprod.access.worldpay.com',
            ste     :'try.access.worldpay.com',
            prod    :'access.worldpay.com'
    ]

    pipeline {

        agent any

        options {
            buildDiscarder( logRotator( numToKeepStr: '10' ) )
            disableConcurrentBuilds()
            timestamps()
            ansiColor('xterm')
            disableResume()
        }

        environment {
            SBT_OPTS = "-Dsbt.global.base=/tmp/.sbtboot -Dsbt.boot.directory=/tmp/.boot -Dsbt.ivy.home=/tmp/.ivy"
            JAVA_OPTS = "-Xms2G -Xmx2G -XX:ReservedCodeCacheSize=512m -XX:MaxMetaspaceSize=1024m -server"
            VAULT_ADDR = "https://${env.VAULT_URL}"
            DOCKER_COMPOSE_FILE = "docker-compose.ci.yml"
            APP_VERSION = sh( script: "printf \$(git rev-parse --short ${env.GIT_COMMIT})",  returnStdout: true )
        }

        parameters {
            booleanParam (name: "Deploy", defaultValue: false, description: "Deploy Service to ${env.ENVIRONMENT}")
            booleanParam (name: 'DEPLOY_SANDBOX', defaultValue: false, description: 'THIS ONLY WORKS WITH NPE JENKINS !!')
            booleanParam (name: 'DryRun', defaultValue: false, description: 'Dry Run')
            booleanParam (name: 'SmokeTests', defaultValue: false, description: 'Only runs smoke tests')
//            booleanParam (defaultValue: false, description: 'Destroy', name: 'Destroy')
        }

        stages {
            stage('Environment Configuration') {
                steps {
                    script {
                        sh "mkdir hc_vault"
                        dir("${env.WORKSPACE}/hc_vault") {

                            sh """
                                curl https://releases.hashicorp.com/vault/0.11.0/vault_0.11.0_linux_amd64.zip --output vault.zip
                                unzip vault.zip
                            """
                        }

                        // Handled the different variant names of Account Verifications
                        if (serviceName == "account-verification") {
                            serviceBucket = "gw2-accountverification-pipelines"
                            appPlayBook = "${serviceName}s-ami-role"
                            jarName = "accountverification-assembly-0.1-SNAPSHOT.jar"
                            terraformServiceName = "verifications"
                            dnsName = "account-verifications"
                            amiIdentifier = "verifications"
                        }

                        if (params.DEPLOY_SANDBOX == true && env.ENVIRONMENT != 'npe') {
                            currentBuild.result = 'ABORTED'
                            error(' ***** You can only do an SANDBOX Deployment using the NPE Jenkins *****')
                        }
                        appHash = "${APP_VERSION}"
                    }
                }
            }

            stage('Tests') {
                when {
                    expression { env.ENVIRONMENT == 'npe' && params.Deploy == false && params.DEPLOY_SANDBOX == false && params.SmokeTests == false }
                }
                steps {
                    script {
                        def vaultToken = vault.getToken(vault_role)
                        sh """
                            export VAULT_TOKEN=${vaultToken}
                            sbt ciBuild
                        """
                    }
                }

                post {
                    always {
                        step([$class: 'JUnitResultArchiver', testResults: "${serviceName}/target/test-reports/*.xml"])
                    }
                }
            }

            stage('Gatling') {
                when {
                    expression { env.BRANCH_NAME != 'master' && env.ENVIRONMENT == 'npe' && params.Deploy == false && params.DEPLOY_SANDBOX == false && params.SmokeTests == false }
                }
                steps {
                    script {
                        def vaultToken = vault.getToken(vault_role)
                        sh """
                            export VAULT_TOKEN=${vaultToken}

                            ./down.sh

                            ./up.sh -f ${DOCKER_COMPOSE_FILE} -c yes
                        """

                        env.KONG_PORT = sh(returnStdout: true, script: "docker-compose -f ${DOCKER_COMPOSE_FILE} port kong 8000 | cut -d: -f2").trim()

                        sh """
                            export GATLING_BASE_URL=http://localhost:${env.KONG_PORT}
                            sbt -Dconfig.file=${env.WORKSPACE}/performance/src/test/resources/acceptance.conf ';gatling:testOnly *WarmUp* ;gatling:testOnly *simulations*'
                        """
                    }
                }

                post {
                    always {
                        sh "./down.sh"
                        step([$class: 'GatlingPublisher', enabled: true])
                    }
                }
            }

            stage('Upload to S3') {
                when {
                    expression { env.BRANCH_NAME == 'master' && env.ENVIRONMENT == 'npe' && params.Deploy == false && params.DEPLOY_SANDBOX == false && params.SmokeTests == false }
                }
                steps {
                    sh "sbt assembly"
                    sh """
                        mkdir ${env.WORKSPACE}/build_artifacts
                        touch ${env.WORKSPACE}/build_artifacts/commit_sh
                        cp ${env.WORKSPACE}/${serviceName}/Dockerfile ${env.WORKSPACE}/build_artifacts/
                        find . -name '*.jar' -exec cp '{}' ${env.WORKSPACE}/build_artifacts/ \\;
                        tar cfz ${env.WORKSPACE}/build_artifacts/build_artifacts-${APP_VERSION}.tgz ${env.WORKSPACE}/build_artifacts/*
                    """

                    sh """
                        aws s3 cp ${env.WORKSPACE}/build_artifacts/build_artifacts-${APP_VERSION}.tgz s3://${serviceBucket}/build_artifacts/build_artifacts-${APP_VERSION}.tgz
                    """
                }
            }

            stage('Pact') {
                when {
                    expression { env.BRANCH_NAME == 'master' && env.ENVIRONMENT == 'npe' && params.Deploy == false && params.DEPLOY_SANDBOX == false && params.SmokeTests == false }
                }
                environment {
                    ACCOUNT_VERIFICATION_PROTO = "https"
                    ACCOUNT_VERIFICATION_HOST = "localhost"
                    ACCOUNT_VERIFICATION_INSECURE = "true"
                }

                steps {
                    script {
                        def vaultToken = vault.getToken(vault_role)
                        sh """
                            export VAULT_TOKEN=${vaultToken}

                            ./down.sh
                            ./up.sh -f ${DOCKER_COMPOSE_FILE} -c yes
                        """

                        env.ACCOUNT_VERIFICATION_PORT = sh(returnStdout: true, script: "docker-compose -f ${DOCKER_COMPOSE_FILE} port kong 8443 | cut -d: -f2").trim()

                        if (serviceName == "account-verification") {
                            dir("${env.WORKSPACE}/pact") {
                                sh "./gradlew pactVerify"

                            }
                        }
                        sh "sbt cdc/clean cdc/it:test cdc/pactPack cdc/pactPush"
                    }
                }

                post {
                    always {
                        sh "./down.sh"
                    }
                }
            }

            stage('Checkmarx') {
                when {
                    expression { env.BRANCH_NAME == 'master' && env.ENVIRONMENT == 'npe' && params.Deploy == false && params.DEPLOY_SANDBOX == false && params.SmokeTests == false }
                }
                environment {
                    REPORT_NAME            = "${serviceName}-checkmarx-report.xml"
                    TGZ_FILE_NAME          = "${serviceName}-jenkins.tgz"
                    CHECKMARX_PROJECT_TYPE = "_prod"
                    S3_PATH                = "${serviceBucket}/checkmarx_scans/${APP_VERSION}"
                }
                steps {
                    checkmarx("${serviceName}", "${serviceBucket}")
                }
            }

            stage('Docker') {
                when {
                    expression { env.BRANCH_NAME == 'master' && env.ENVIRONMENT == 'npe' && params.Deploy == false && params.DEPLOY_SANDBOX == false && params.SmokeTests == false }
                }
                steps {
                    dir("${env.WORKSPACE}/build_artifacts") {
                        script {
                            def image = docker.build(
                                    "com-worldpay-gateway-${serviceName}/app",
                                    "--build-arg JAR_FILE=${jarName} ${env.WORKSPACE}/build_artifacts/"
                            )
                            for (r in ECR_REGIONS) {
                                sh "\$(aws ecr get-login --no-include-email --region ${r})"
                                docker.withRegistry("https://${env.AWS_ACCOUNT_ID}.dkr.ecr.${r}.amazonaws.com") {
                                    image.push("${APP_VERSION}")
                                    image.push("latest")
                                }
                            }
                        }
                    }
                }
            }

            stage("AMI") {
                when {
                    expression { env.BRANCH_NAME == 'master' && env.ENVIRONMENT == 'npe' && params.Deploy == false && params.DEPLOY_SANDBOX == false && params.SmokeTests == false }
                }
                steps {
                    provisionAmiRole("${terraformServiceName}")
                    gitCloneInto("${appPlayBook}", "${env.WORKSPACE}/ami_prov/ami-provisioning-module/ansible/role_staging", '*/master')
                    provisionAmi("eu-west-1", "${terraformServiceName}", "${appPlayBook}")
                }
            }

            stage('Deploy') {

                when {
                    expression { env.BRANCH_NAME == 'master' && env.ENVIRONMENT == 'npe' && params.SmokeTests == false || params.Deploy == true || params.DEPLOY_SANDBOX == true }
                }

                stages {
                    stage("Ireland") {
                        steps {
                            script {
                                infHash = provisionService('eu-west-1', "${terraformServiceName}","${dnsName}", "${amiIdentifier}", "${serviceName}")
                            }
                        }
                    }
                    stage("London") {
                        when {
                            expression { env.ENVIRONMENT != 'npe' }
                        }
                        steps {
                            script {
                                infHash = provisionService('eu-west-2', "${terraformServiceName}","${dnsName}", "${amiIdentifier}", "${serviceName}")
                            }
                        }
                    }
                    stage("Frankfurt") {
                        when {
                            expression { env.ENVIRONMENT != 'npe' }
                        }
                        steps {
                            script {
                                infHash = provisionService('eu-central-1', "${terraformServiceName}","${dnsName}", "${amiIdentifier}", "${serviceName}")
                            }
                        }
                    }
                }
            }

            stage('Smoke') {
                when {
                    expression { env.BRANCH_NAME == 'master' && (env.ENVIRONMENT == 'npe' || env.ENVIRONMENT == 'preprod') && params.DryRun == false}
                }
                environment {
                    SMOKE_VAULT_CREDS = "secret/${serviceName}/smoke-test-${env.ENVIRONMENT}-configuration-creds"
                    SMOKE_ACCEPT_ANY_CERT = "false"
                }
                steps {
                    script {
                        if (env.ENVIRONMENT == 'ste') {
                            vault_role = "jenkinsWithEnvForAnsibleSTE-eu-west-1"
                        }

                        def vaultToken = vault.getToken(vault_role)

                        sh """
                            export VAULT_TOKEN=${vaultToken}
                            export SMOKE_URL=https://${fqdnMap.get(env.ENVIRONMENT)}
                            export SMOKE_VAULT_CREDENTIALS='${env.SMOKE_VAULT_CREDS}'

                            sbt "project smoke" smoke:test
                        """
                    }
                }
            }
        }

        post {
            always {
                script {

                    def info = ""
                    def postSlack = true

                    if (params.Destroy != null && params.Destroy) {
                        info = "*Destroy*"
                    }
                    else if (params.SmokeTests == true) {
                        info = "Smoke Tests"
                    }
                    else if (params.DEPLOY_SANDBOX == true && env.ENVIRONMENT != 'npe') {
                        info = "You can only do a SANDBOX Deployment using NPE Jenkins"

                    }  else if (params.Deploy == true && params.DEPLOY_SANDBOX == true) {
                        info = "Sandbox Deploy"

                    } else if (env.BRANCH_NAME != 'master' && env.ENVIRONMENT == 'npe') {
                        info = "PR Check"

                    } else if (env.BRANCH_NAME == 'master' && env.ENVIRONMENT == 'npe' && params.Deploy == false) {
                        info = "Create Docker/AMI and Deploy"

                    } else if (params.Deploy == true) {
                        info = "Deploy \nApp Version: ${appHash} \nInfra Version: ${infHash}"

                    } else {
                        postSlack = false
                    }

                    if (postSlack) {
                        slackToJenkins( "", "${info}")
                    }
                }
            }
        }
    }
}