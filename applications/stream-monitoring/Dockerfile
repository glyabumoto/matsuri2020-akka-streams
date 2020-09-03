FROM centos:7

RUN yum update -y

# Install Java
RUN yum install java-1.8.0-openjdk-devel -y
RUN java -version

# install application
RUN mkdir /var/scalamatsuri
RUN mkdir /var/scalamatsuri/bin
ADD ./target/scala-2.13/stream-monitoring.jar /var/scalamatsuri/bin/stream-monitoring.jar

# create work dir
RUN mkdir /var/log/scalamatsuri

# start application
ADD startup.sh /root/startup.sh
RUN chmod +x /root/startup.sh
