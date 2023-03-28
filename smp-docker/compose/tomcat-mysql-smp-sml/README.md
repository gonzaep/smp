# Experimental SMP docker image
Purpose of compose plan is to startup fully functional SMP environment for demo and testing. The plan contains. 
 - MySql database
 - SML services
 - CAS instance (EULOGIN)
 - email server (inbucket)


# Run environment
docker-compose -f docker-compose.yml up -d 


## SMP 
url: http://localhost:8982/smp/

http://eulogin.protected.smp.local:8982/smp/
eulogin.protected.smp.local

### MYSQL 
Database client connection (for testing and debugging )
url: jdbc:mysql://localhost:3908/smp
Username: smp
Password: smp

### Volume (-v /opt/dockerdata/sml:/data)
Mysql database files and tomcat configuration (and logs) can be externalized for experimenting with different SMP settings.


## SML 
url: http://localhost:8982/edelivery-sml/

### MYSQL 
Database client connection (for testing and debugging )
url: jdbc:mysql://localhost:3908/sml
Username: sml
Password: sml

## CAS - EULOGIN 
url: https://localhost:7102/cas/login
Users:
 - user/123456
 - smp/123456
 - system/123456

 To use eulogin add the following hostname mappings (Linux: /etc/hosts, windows: C:\Windows\System32\drivers\etc\hosts
                                                                                 
 - 127.0.0.1 eulogin-mock-server
 - 127.0.0.1 eulogin.protected.smp.local


## Mail server
url: http://172.19.0.2:9005/monitor 

