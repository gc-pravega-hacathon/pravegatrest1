/**
 * @Author: hce
 * 只提交了部分涉及Pravega的代码，非运行程序
 */

package main

import (
	//"encoding/json"

	"fmt"
	"net"
	"os"
	"runtime"
	"strconv"
	"time"
	jsoniter "github.com/json-iterator/go"
	"github.com/fasthttp/router"
	"github.com/valyala/fasthttp"
)


func main() {
	glVer = "1"	
	printClr(":初始化计算模块完成", bclr["red"])

	//初始化mqtt客户端
	initMQTTclient()

	//可以用来给外部需求获取数据的任务，这部分在未来用来给其他设备提供数据用
	printClr(":正在启动WEB服务...", bclr["yellow"])
	mux := router.New()
	mux.POST("/delltest", funDellproc)
	
	fasthttp.ListenAndServe(":8889", mux.Handler)

	printClr(":WEB服务已经启动...", bclr["yellow"])
	printClr("!服务已停止", bclr["red"])
}


func funDellproc(ctx *fasthttp.RequestCtx) {
	s := ctx.Request.Body()	
	now := time.Now().Unix()
	mqttsend("topic/25/set", "{\"v\":1,\"prop\":{\"c\":47,\"t\":\"3412ae65ee503as3d3001\",\"v\":\""+string(s)+"\"},\"tt\":"+strconv.FormatInt(now, 10)+" }")
}
