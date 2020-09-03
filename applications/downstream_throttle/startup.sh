#!/bin/bash

# start java
prog=downstream-throttle.jar
jarprog=/var/scalamatsuri/bin/${prog}
javaoptions="-server"

# do not use xray for now
# /root/xray/xray -o -n ap-northeast-1 &

java -cp ${jarprog} -jar ${javaoptions} ${jarprog} ${@+"$@"}
