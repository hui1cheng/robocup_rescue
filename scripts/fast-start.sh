#!/bin/bash

# 服务器代码路径
SERVER_DIR="/e/rescue/rcrs-server"
# 队伍代码路径
TEAM_DIR="/e/rescue/Rangel"

OPTIONS=(
  "服务器代码编译"
  "队伍代码编译"
  "服务器代码预计算启动"
  "队伍代码预计算启动"
  "服务器代码正式启动"
  "队伍代码正式启动"
  "队伍代码正式启动(DEBUG MODE)"
  "队伍代码正式启动(仅连接警察中心代理和警察部队)"
  "退出"
)

MAPS=($(ls "${SERVER_DIR}"/maps/))

function selectMap() {
  echo "请选择地图:"
  select map in "${MAPS[@]}"; do
    MAP=$map
    break
  done
}

function serverBuild() {
  cd ${SERVER_DIR}
  ./gradlew completeBuild
}

function teamBuild() {
  cd ${TEAM_DIR}
  ./gradlew build
}

function serverPre() {
  selectMap
  cd ${SERVER_DIR}/scripts/
  bash start-precompute.sh -m ../maps/"$MAP"/map/ -c ../maps/"$MAP"/config/
  echo "服务器代码预计算启动"
}

function teamPre() {
  cd ${TEAM_DIR}/scripts
  bash launch.sh -pre 1 -t 1,0,1,0,1,0 -local && PID=$$
  sleep 120
  kill $PID
  echo "队伍代码预计算启动"
}

function serverStart() {
  selectMap
  cd ${SERVER_DIR}/scripts/
  bash start-comprun.sh -m ../maps/"$MAP"/map/ -c ../maps/"$MAP"/config/
  echo "服务器代码正式启动"
}

function teamStart() {
  cd ${TEAM_DIR}
  rm -rf ./logs/*
  cd ./scripts
  bash launch.sh -all
  echo "队伍代码正式启动"
}

function teamDebug() {
  cd ${TEAM_DIR}
  rm -rf ./logs/*
  cd ./scripts
  bash launch.sh -debug -all
  echo "队伍代码正式启动(DEBUG MODE)"
}

function teamStartOnlyPolice() {
  cd ${TEAM_DIR}
  rm -rf ./logs/*
  cd ./scripts
  bash launch.sh -debug -t 0,0,-1,-1,0,0
  echo "队伍代码正式启动(仅连接警察中心代理和警察部队)"
}

echo "请选择启动模式:"
select option in "${OPTIONS[@]}"; do
  case $option in
  "服务器代码编译")
    serverBuild
    ;;
  "队伍代码编译")
    teamBuild
    ;;
  "服务器代码预计算启动")
    serverPre
    ;;
  "队伍代码预计算启动")
    teamPre
    ;;
  "服务器代码正式启动")
    serverStart
    ;;
  "队伍代码正式启动")
    teamStart
    ;;
  "队伍代码正式启动(DEBUG MODE)")
    teamDebug
    ;;
  "队伍代码正式启动(仅连接警察中心代理和警察部队)")
    teamStartOnlyPolice
    ;;
  "退出")
    break
    ;;
  *)
    echo "请输入正确的选项"
    ;;
  esac
done
