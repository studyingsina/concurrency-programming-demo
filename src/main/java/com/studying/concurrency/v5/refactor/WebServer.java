package com.studying.concurrency.v5.refactor;

import com.studying.concurrency.util.Logs;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLConnection;

/**
 * Created by junweizhang on 17/11/23.
 * 第五版 增加工作线程-线程池.
 * 抽象出五个角色:
 * Bootstrap-启动器
 * WebServer-Web服务器
 * Worker-处理HTTP请求的工作者.
 * Acceptor-监听器
 * Queue-任务队列
 * ThreadPool-线程池
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

    // 处理HTTP请求线程池
    private ThreadPool threadPool;

    // 监听Socket线程
    private Thread acceptorThread;

    public WebServer(int port, File docRoot) throws Exception {
        // 1. 服务端启动8080端口，并一直监听；
        this.port = port;
        this.ss = new ServerSocket(port, 10);
        this.docRoot = docRoot;
        start();
    }

    /**
     * 必需先启动工作线程,再启动监听线程.
     */
    private void start() {
        // 启动工作线程,工作线程,可以作为守护线程
        threadPool = new ThreadPool(2);

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
                    Socket s = listen();
                    Logs.SERVER.info("acceptor a new socket : {}", s);
                    // assign(s);
                    threadPool.assign(s);
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

        // 工作线程
        private Thread workerThread;

        // 工作线程所在的线程池对象
        private ThreadPool pool;

        public Worker(ThreadPool pool, int index) {
            this.pool = pool;
            workerThread = new Thread(this);
            workerThread.setName("worker-process-thread-" + index);
            workerThread.setDaemon(true);
            workerThread.start();
        }

        @Override
        public void run() {
            try {
                while (!isStop && !workerThread.isInterrupted()) {
                    Socket s = pool.await();
                    if (s != null) {
                        Logs.SERVER.info("worker begin process socket : {}", s);
                        process(s);
                    }
                }
            } catch (Exception e) {
                Logs.SERVER.error("Worker process error", e);
            }
        }

    }

    /**
     * 一个简单的阻塞队列(先进先出),线程安全,不支持扩容,用数组实现.
     */
    public class SimpleQueue<E> {

        // 元素数据
        private Object[] items;

        // 队列容量
        private int capacity;

        // 队列头索引
        private int putIndex;

        // 队列尾索引
        private int takeIndex;

        // 队列当前元素个数
        private int size;

        public SimpleQueue(int cap) {
            this.capacity = cap;
            this.items = new Object[cap];
            this.size = 0;
            this.putIndex = 0;
            this.takeIndex = 0;
        }

        public synchronized void put(E e) throws InterruptedException {
            // 监听器线程往队列中放入socket,如果当前队列满了则监听器等待
            while (isFull()) {
                Logs.SERVER.info("{} wait put queue : {}", Thread.currentThread().getName(), e);
                wait();
            }
            // 若队列没满,则监听器线程往队列中放入socket;并且如果先前已经有工作线程在等待取数据,通知工作线程来取
            items[putIndex] = e;
            putIndex = (putIndex + 1) % capacity;
            size++;
            Logs.SERVER.info("queue isFull {}, isEmpty {}, capacity {}, size {}, takeIndex {}, putIndex {}", isFull(), isEmpty(),
                    capacity, size, takeIndex, putIndex);
            notifyAll();
        }

        public synchronized E take() throws InterruptedException {
            // 工作线程来取socket,如果当前队列为空,则工作线程进行等待
            while (isEmpty()) {
                Logs.SERVER.info("{} wait get socket", Thread.currentThread().getName());
                wait();
            }
            // 队列不为空,工作线程从队列中取出socket;并且如果先前有监听器线程在等待往队列中放数据,通知监听器线程放
            E e = (E) items[takeIndex];
            // 将已经取走的引用置为空,让GC可以回收
            items[takeIndex] = null;
            takeIndex = (takeIndex + 1) % capacity;
            size--;
            Logs.SERVER.info("queue isFull {}, isEmpty {}, capacity {}, size {}, takeIndex {}, putIndex {}", isFull(), isEmpty(),
                    capacity, size, takeIndex, putIndex);
            notify();
            return e;
        }

        public synchronized boolean isFull() {
            return capacity == size;
        }

        public synchronized boolean isEmpty() {
            return size == 0;
        }

    }

    /**
     * 一个简单的固定大小线程池,数据实现,任务先放入阻塞队列,工作线程不断去队列中取任务.
     */
    public class ThreadPool {

        // 监听到的socket队列
        private SimpleQueue<Socket> socketQueue;

        // 线程组
        private Worker[] workers;

        public ThreadPool(int poolSize) {
            this.socketQueue = new SimpleQueue<>(3);
            workers = new Worker[poolSize];
            for (int i = 0; i < poolSize; i++) {
                workers[i] = new Worker(this, i);
            }
            Logs.SERVER.info("start workerPool size : {} ...", poolSize);
        }

        /**
         * 由监听线程往队列中放入socket,以备工作线程从中取值进行处理.
         */
        private void assign(Socket socket) throws Exception {
            socketQueue.put(socket);
        }

        /**
         * 工作线程从队列中取出socket.
         */
        private Socket await() throws Exception {
            return socketQueue.take();
        }
    }

}
