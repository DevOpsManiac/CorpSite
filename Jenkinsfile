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
                sh 'scp -r target/globex-web.war ubuntu@corp-uat.sndevops.xyz:/opt/tomcat/webapps'
            }
        }
        stage('UAT test') {
            parallel {
                stage('UAT unit test') {
                    steps {
                        echo 'UAT unit tests'
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
                echo 'PROD'
            }
        }
    }
}