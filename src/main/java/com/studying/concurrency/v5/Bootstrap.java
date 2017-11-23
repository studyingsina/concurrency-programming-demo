package com.studying.concurrency.v5;

import com.studying.concurrency.util.Logs;

import java.io.File;
import java.net.URL;

/**
 * Created by junweizhang on 17/11/23.
 * 第五版 增加工作线程-线程池.
 * 抽象出五个角色:
 *      Bootstrap-启动器
 *      WebServer-Web服务器
 *      Worker-处理HTTP请求的工作者.
 *      Acceptor-监听器
 *      Queue-任务队列
 */
public class Bootstrap {

    /**
     * 我是启动器,只做参数初始化等相关工作.
     * @param args
     */
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
