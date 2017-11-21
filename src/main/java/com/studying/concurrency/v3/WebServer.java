package com.studying.concurrency.v3;

import com.studying.concurrency.util.Logs;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLConnection;

/**
 * Created by junweizhang on 17/11/21.
 * 第二版 WebServer 重构,将main线程和服务线程分离.
 * 抽象出三个角色:
 * Bootstrap-启动器
 * WebServer-Web服务器
 * Worker-处理HTTP请求的工作者.
 */
public class WebServer {

    // ServerSocket
    private ServerSocket ss;

    // 根目录
    private File docRoot;

    // 服务是否停止
    private boolean isStop = false;

    // HTTP监听端口
    private int port = 8080;

    // 处理HTTP请求线程
    private Thread workerThread;

    // 监听Socket线程
    private Thread acceptorThread;

    // 监听到的socket
    private Socket socket;

    // 表示当前监听到的socket是否可用
    private boolean available;

    public WebServer(int port, File docRoot) throws Exception {
        // 1. 服务端启动8080端口，并一直监听；
        this.port = port;
        this.ss = new ServerSocket(port, 10);
        this.docRoot = docRoot;
        start(this);
    }

    /**
     * 必需先启动工作线程,再启动监听线程.
     */
    private void start(WebServer server) {
        // 启动工作线程,工作线程,可以作为守护线程
        workerThread = new Thread(new Worker());
        workerThread.setName("worker-process-thread");
        workerThread.setDaemon(true);
        workerThread.start();
        Logs.SERVER.info("start worker thread : {} ...", workerThread.getName());

        // 启动监听线程,监听线程,不作为守护线程,保证JVM不退出.
        acceptorThread = new Thread(new Acceptor());
        acceptorThread.setName("http-acceptor-" + port + "-thread");
        acceptorThread.start();
        Logs.SERVER.info("start acceptor thread : {} ...", acceptorThread.getName());
    }

    public void serve() {
        Logs.SERVER.info("Http Server ready to receive requests...");
        while (!isStop) {
            try {
                Socket socket = listen();
                process(socket);
            } catch (Exception e) {
                Logs.SERVER.error("serve error", e);
                isStop = true;
                // System.exit(1);
            }
        }
    }

    /**
     * 2. 监听到有客户端（比如浏览器）要请求http://localhost:8080/，那么建议连接，TCP三次握手；
     */
    private Socket listen() throws IOException {
        return ss.accept();
    }

    /**
     * 由监听线程给socket赋值,以备工作线程从中取值进行处理.
     */
    private synchronized void assign(Socket socket) throws Exception {
        while (available) {
            Logs.SERVER.info("{} wait assign socket : {}", Thread.currentThread().getName(), socket);
            this.wait();
        }

        this.socket = socket;
        available = true;
        this.notify();
    }

    /**
     * 工作线程取出当前的socket.
     */
    private synchronized Socket await() throws Exception {
        while (!available) {
            Logs.SERVER.info("{} wait get socket", Thread.currentThread().getName());
            this.wait();
        }

        Socket socket = this.socket;
        available = false;
        this.notify();
        return socket;
    }

    /**
     * 3. 处理接收到的Socket,解析输入字节流,并返回结果.
     */
    private void process(Socket socket) throws Exception {

        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        /**
         * 3. 建立连接后，读取此次连接客户端传来的内容（其实就是解析网络字节流并按HTTP协议去解析）；
         * GET /dir1/dir2/file.html HTTP/1.1
         */
        String requestLine = reader.readLine();
        Logs.SERVER.info("requestLine is : {}", requestLine);
        if (requestLine == null || requestLine.length() < 1) {
            Logs.SERVER.error("could not read request");
            return;
        }

        String[] tokens = requestLine.split(" ");
        String method = tokens[0];
        String fileName = tokens[1];
        File requestedFile = docRoot;

        String[] paths = fileName.split("/");
        for (String path : paths) {
            requestedFile = new File(requestedFile, path);
        }
        if (requestedFile.exists() && requestedFile.isDirectory()) {
            requestedFile = new File(requestedFile, "index.html");
        }

        BufferedOutputStream bos = new BufferedOutputStream(os);
        // 4. 解析到请求路径（比如此处是根路径），那么去根路径下找资源（比如此处是index.html文件）；
        if (requestedFile.exists()) {
            Logs.SERVER.info("return 200 ok");
            long length = requestedFile.length();
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(requestedFile));
            String contentType = URLConnection.guessContentTypeFromStream(bis);
            byte[] headerBytes = createHeaderBytes("HTTP/1.1 200 OK", length, contentType);
            bos.write(headerBytes);

            // 5. 找到资源后，再通过网络流将内容输出，当然，还是按照HTTP协议去输出，这样客户端（浏览器）就能正常渲染、显示网页内容；
            byte[] buf = new byte[2000];
            int blockLen;
            while ((blockLen = bis.read(buf)) != -1) {
                bos.write(buf, 0, blockLen);
            }
            bis.close();
        } else {
            Logs.SERVER.info("return 404 not found");
            byte[] headerBytes = createHeaderBytes("HTTP/1.0 404 Not Found", -1, null);
            bos.write(headerBytes);
        }
        bos.flush();
        socket.close();
    }

    /**
     * 生成HTTP Response头.
     *
     * @param content
     * @param length
     * @param contentType
     * @return
     */
    private byte[] createHeaderBytes(String content, long length, String contentType) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(baos));
        bw.write(content + "\r\n");
        if (length > 0) {
            bw.write("Content-Length: " + length + "\r\n");
        }
        if (contentType != null) {
            bw.write("Content-Type: " + contentType + "\r\n");
        }
        bw.write("\r\n");
        bw.flush();
        byte[] data = baos.toByteArray();
        bw.close();
        return data;
    }

    /**
     * 接收器,监听HTTP端口,接收Socket.
     */
    public class Acceptor implements Runnable {

        @Override
        public void run() {
            try {
                while (!isStop) {
                    Logs.SERVER.info("acceptor begin listen socket ...");
                    Socket socket = listen();
                    Logs.SERVER.info("acceptor a new socket : {}", socket);
                    assign(socket);
                }
            } catch (Exception e) {
                Logs.SERVER.error("Acceptor process error", e);
            }
        }
    }

    /**
     * 处理HTTP请求的工作者.
     */
    public class Worker implements Runnable {

        @Override
        public void run() {
            try {
                while (!isStop) {
                    Socket s = await();
                    if (s != null) {
                        Logs.SERVER.info("worker begin process socket : {}", socket);
                        process(s);
                        socket = null;
                    }
                }
            } catch (Exception e) {
                Logs.SERVER.error("Worker process error", e);
            }
        }

    }

}
