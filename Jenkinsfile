pipeline {
    agent any
    tools { 
        maven 'Jenkins Maven' 
    }
    stages {
        stage('CI') {
            steps {
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
            parallel {
                stage('UAT unit test') {
                    steps {
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
                stage('UAT functional test') {
                    steps {
                        echo 'UAT functional tests'
                    }
                }
            }
        }   
        stage('PROD') {
            steps {
                script {
                    //snDevOps
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