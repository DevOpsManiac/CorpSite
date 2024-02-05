pipeline {
    agent any
    stages {
        stage('CI') {
            steps {
                snDevOpsStep()
                sh 'mvn compile'
                sh 'mvn verify'
                withSonarQubeEnv('SonarQube') {
                sh "mvn clean package sonar:sonar"
              }
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
               
                sh 'mvn package'
                sh 'cp -vfr target/globex-web.war /opt/tomcat/webapps/globex-uat.war'
                sh 'sudo systemctl stop tomcat9.service'
                sh 'echo ################ Reiniciando o Tomcat #############'
                sh 'sudo systemctl start tomcat9.service'
            
            }
        }

    
     stage('UAT Homologacao') {
            steps {
                snDevOpsStep()
               
                sh 'mvn verify'
                sh 'cp -vfr target/globex-web.war /opt/tomcat/webapps/globex-hom.war'
                sh 'sudo systemctl stop tomcat9.service'
                sh 'echo ################ Reiniciando o Tomcat #############'
                sh 'sudo systemctl start tomcat9.service'
            
            }
          post {
                success {
                    junit '**/target/surefire-reports/*.xml' 
                }
            }
        }
    
    stage('Producao') {
            steps {
                snDevOpsStep()
                snDevOpsChange()
                sh 'cp -vfr target/globex-web.war /opt/tomcat/webapps/globex-prod.war'
                sh 'sudo systemctl stop tomcat9.service'
                sh 'echo ################ Reiniciando o Tomcat #############'
                sh 'sudo systemctl start tomcat9.service'
            
            }
        }
    }
}
    
