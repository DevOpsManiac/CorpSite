sudo apt-get install curl nginx default-jdk
sudo groupadd tomcat
sudo useradd -s /bin/false -g tomcat -d /opt/tomcat tomcat
cd /tmp
curl -O http://apache.mirrors.ionfish.org/tomcat/tomcat-8/v8.5.38/bin/apache-tomcat-8.5.38.tar.gz
sudo mkdir /opt/tomcat
sudo tar -xzvf apache-tomcat-8.5.38.tar.gz -C /opt/tomcat --strip-components=1
cd /opt/tomcat
sudo chgrp -R tomcat /opt/tomcat
sudo chmod -R g+r conf
sudo chmod g+x conf
sudo chown -R tomcat webapps/ work/ temp/ logs/
sudo update-java-alternatives -l
sudo cat > /etc/systemd/system/tomcat.service <<EOF
[Unit]
Description=Apache Tomcat Web Application Container
After=network.target

[Service]
Type=forking

Environment=JAVA_HOME=/usr/lib/jvm/java-1.11.0-openjdk-amd64
Environment=CATALINA_PID=/opt/tomcat/temp/tomcat.pid
Environment=CATALINA_HOME=/opt/tomcat
Environment=CATALINA_BASE=/opt/tomcat
Environment='CATALINA_OPTS=-Xms512M -Xmx1024M -server -XX:+UseParallelGC'
Environment='JAVA_OPTS=-Djava.awt.headless=true -Djava.security.egd=file:/dev/./urandom'

ExecStart=/opt/tomcat/bin/startup.sh
ExecStop=/opt/tomcat/bin/shutdown.sh

User=tomcat
Group=tomcat
UMask=0007
RestartSec=10
Restart=always

[Install]
WantedBy=multi-user.target

EOF 

sudo systemctl daemon-reload
sudo systemctl start tomcat

sudo cat > /etc/nginx/sites-enabled/defaul <<EOF
server {
        listen 80;
        server_name _;
        root /var/www/html;

        location / {
                proxy_pass        http://localhost:8080;
                proxy_set_header  X-Real-IP $remote_addr;
                proxy_set_header  X-Forwarded-For $proxy_add_x_forwarded_for;
                proxy_set_header  Host $http_host;
    }
}
EOF
sudo service nginx restart


# add to /opt/tomcat/conf/server.xml above </host>
#<Context path="" docBase="globex-web">
#    <!-- Default set of monitored resources -->
#    <WatchedResource>WEB-INF/web.xml</WatchedResource>
#</Context>