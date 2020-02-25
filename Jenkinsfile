pipeline {
    agent any
    tools { 
        maven 'Jenkins Maven' 
    }
    stages {
        stage('CI') {
            steps {
                snDevOpsStep()
                sh '''
                    export M2_HOME=/opt/apache-maven-3.6.0 # your Mavan home path
                    export PATH=$PATH:$M2_HOME/bin
                    mvn --version
                '''
                sh 'mvn compile'
                sh 'mvn verify'
            }
            post {
                success {
                    junit '**/target/surefire-reports/*.xml' 
                }
            }
        }
        stage('UAT deploy') {
            steps {
                snDevOpsStep()
                sh '''
                    export M2_HOME=/opt/apache-maven-3.6.0 # your Mavan home path
                    export PATH=$PATH:$M2_HOME/bin
                    mvn --version
                '''
                sh 'mvn package'

                script {
                    sshPublisher(continueOnError: false, failOnError: true,
                    publishers: [
                        sshPublisherDesc(
                            configName:'CorpSite UAT',
                            verbose: true,
                            transfers: [
                                sshTransfer(
                                    sourceFiles: 'target/globex-web.war',
                                    removePrefix: 'target/',
                                    remoteDirectory: '/opt/tomcat/webapps'
                                )
                            ]
                        )
                    ])
                }
            }
        }
        stage('UAT test') {
            steps {
                snDevOpsStep()
                sh '''
                    export M2_HOME=/opt/apache-maven-3.6.0 # your Mavan home path
                    export PATH=$PATH:$M2_HOME/bin
                    mvn --version
                '''
                sh 'mvn compile'
                sh 'mvn verify'
            }
            post {
                success {
                    junit '**/target/surefire-reports/*.xml' 
                }
            }
        
            steps {
                sh '''
                    export M2_HOME=/opt/apache-maven-3.6.0 # your Mavan home path
                    export PATH=$PATH:$M2_HOME/bin
                    mvn --version
                '''
                sh 'mvn compile'
                /*
                sh '''
                    mvn sonar:sonar \
                    -Dsonar.projectKey=CorpSite \
                    -Dsonar.host.url=http://sonarqube.sndevops.xyz:9000 \
                    -Dsonar.login=efef5144be738a606c23fff3f139f00965b82869 \
                    -Dsonar.exclusions=src/main/webapp/resources/js/bootstrap.js \
                    -Dsonar.analysis.scm=$GIT_COMMIT \
                    -Dsonar.analysis.buildURL=$BUILD_URL
                '''
                */
            }
        }
        stage('deploy') {
            steps {
                snDevOpsStep()
                snDevOpsChange()
                script {
                    sshPublisher(continueOnError: false, failOnError: true,
                    publishers: [
                        sshPublisherDesc(
                            configName:'CorpSite PROD',
                            verbose: true,
                            transfers: [
                                sshTransfer(
                                    sourceFiles: 'target/globex-web.war',
                                    removePrefix: 'target/',
                                    remoteDirectory: '/opt/tomcat/webapps'
                                )
                            ]
                        )
                    ])
                }
            }
        }
    }
}
