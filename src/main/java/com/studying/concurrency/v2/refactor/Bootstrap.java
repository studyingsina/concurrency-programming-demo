package com.studying.concurrency.v2.refactor;

import com.studying.concurrency.util.Logs;

import java.io.File;
import java.net.URL;

/**
 * Created by junweizhang on 17/11/21.
 * 第二版 WebServer 重构,将main线程和服务线程分离.
 * 抽象出三个角色:
 *      Bootstrap-启动器
 *      WebServer-Web服务器
 *      Worker-处理HTTP请求的工作者.
 */
public class Bootstrap {

    public static void main(String[] args) {
        try {
            int port = 8080;
            String docRootStr = "htmldir";
            URL url = Bootstrap.class.getClassLoader().getResource(docRootStr);
            File docRoot = new File(url.toURI());
            WebServer webServer = new WebServer(port, docRoot);
            Logs.SERVER.info("init webServer : {}", webServer);
            Logs.SERVER.info("我是main线程, 好开心, 我已经被释放出来了, 可以做些其它的事情...");
        } catch (Exception e) {
            Logs.SERVER.error("main start error", e);
            System.exit(1);
        }
    }


}
