package com.studying.concurrency.v1;

import com.studying.concurrency.util.Logs;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by junweizhang on 17/11/20.
 * 第一版 WebServer
 */
public class BootstrapV1 {

    private ServerSocket ss;

    private File docRoot;

    private boolean isStop = false;

    public BootstrapV1(int port, File docRoot) throws Exception {
        this.ss = new ServerSocket(port, 10);
        this.docRoot = docRoot;
    }

    public void serve() {
        Logs.SERVER.info("Http Server ready to receive requests...");
        while (!isStop) {
            try {
                process();
            } catch (Exception e) {
                Logs.SERVER.info("serve error", e);
                isStop = true;
                // System.exit(1);
            }
        }


    }

    private void process() throws Exception {
        Socket socket = ss.accept();
        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        /**
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

        if (requestedFile.exists()) {
            Logs.SERVER.info("return 200 ok");
            long length = requestedFile.length();
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(requestedFile));
            String contentType = URLConnection.guessContentTypeFromStream(bis);
            byte[] headerBytes = createHeaderBytes("HTTP/1.1 200 OK", length, contentType);
            bos.write(headerBytes);

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

    public static void main(String[] args) {
        try {
            int port = 8080;
            String docRootStr = "htmldir";
            URL url = BootstrapV1.class.getClassLoader().getResource(docRootStr);
            File docRoot = new File(url.toURI());
            BootstrapV1 bootstrap = new BootstrapV1(port, docRoot);
            bootstrap.serve();
        } catch (Exception e) {
            Logs.SERVER.error("main start error", e);
            System.exit(1);
        }
    }

}
