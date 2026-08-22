package core;

import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;

public class SpeedTest {

    public interface SpeedTestListener {
        void onProgress(int percent, double currentMbps);
        void onComplete(double finalMbps, long pingMs);
        void onError(String error);
    }

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 30000;

    private static InputStream openUrl(String url) throws Exception {
        URLConnection conn = URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        return conn.getInputStream();
    }

    public static void runTest(SpeedTestListener listener) {
        Thread t = new Thread(() -> {
            try {
                // 1. Ping test
                long pingStart = System.currentTimeMillis();
                try (InputStream is = openUrl("https://speed.cloudflare.com/__down?bytes=10")) {
                    is.readAllBytes();
                }
                long pingMs = System.currentTimeMillis() - pingStart;

                // 2. Download speed test (50MB chunk)
                long totalBytes = 50_000_000;
                long startTime = System.currentTimeMillis();
                long lastReportTime = startTime;
                long bytesRead = 0;

                try (InputStream is = openUrl("https://speed.cloudflare.com/__down?bytes=50000000")) {
                    byte[] buffer = new byte[65536];
                    int read;

                    while ((read = is.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) break;
                        bytesRead += read;
                        long now = System.currentTimeMillis();

                        if (now - lastReportTime >= 200) {
                            long timeDiff = now - startTime;
                            double mbps = (bytesRead * 8.0 / 1_000_000.0) / (timeDiff / 1000.0);
                            int percent = (int) ((bytesRead * 100) / totalBytes);
                            javax.swing.SwingUtilities.invokeLater(() -> listener.onProgress(percent, mbps));
                            lastReportTime = now;
                        }
                    }
                }
                
                if (Thread.currentThread().isInterrupted()) return;

                long endTime = System.currentTimeMillis();
                double totalTimeSeconds = (endTime - startTime) / 1000.0;
                if (totalTimeSeconds <= 0.001) totalTimeSeconds = 0.001;
                double finalMbps = (bytesRead * 8.0 / 1_000_000.0) / totalTimeSeconds;

                javax.swing.SwingUtilities.invokeLater(() -> listener.onComplete(finalMbps, pingMs));

            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                javax.swing.SwingUtilities.invokeLater(() -> listener.onError(msg));
            }
        }, "SpeedTest-Thread");
        t.setDaemon(true);
        t.start();
    }
}

