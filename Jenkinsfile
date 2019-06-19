pipeline {
    agent any
    tools { 
        maven 'Maven 3.6.0' 
        jdk 'jdk8' 
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
                echo 'UAT deploy'
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