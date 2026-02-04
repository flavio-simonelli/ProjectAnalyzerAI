package org.apache.bookkeeper.benchmark;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter; // AGGIUNTO per reportResults
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.bookkeeper.client.BKException;
import org.apache.bookkeeper.client.BookKeeper;
import org.apache.bookkeeper.client.LedgerHandle;
import org.apache.bookkeeper.client.AsyncCallback.AddCallback;
import org.apache.bookkeeper.conf.ClientConfiguration;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Charsets.UTF_8;

public class BenchThroughputLatency implements AddCallback, Runnable {
    static Logger LOG = LoggerFactory.getLogger(BenchThroughputLatency.class);

    BookKeeper bk;
    LedgerHandle lh[];
    AtomicLong counter;

    Semaphore sem;
    int numberOfLedgers = 1;
    final int sendLimit;
    final long latencies[];

    static class Context {
        long localStartTime;
        long id;

        Context(long id, long time){
            this.id = id;
            this.localStartTime = time;
        }
    }

    public BenchThroughputLatency(int ensemble, int writeQuorumSize, int ackQuorumSize, byte[] passwd,
                                  int numberOfLedgers, int sendLimit, ClientConfiguration conf)
            throws KeeperException, IOException, InterruptedException {
        this.sem = new Semaphore(conf.getThrottleValue());
        bk = new BookKeeper(conf);
        this.counter = new AtomicLong(0);
        this.numberOfLedgers = numberOfLedgers;
        this.sendLimit = sendLimit;
        this.latencies = new long[sendLimit];
        try{
            lh = new LedgerHandle[this.numberOfLedgers];

            for(int i = 0; i < this.numberOfLedgers; i++) {
                lh[i] = bk.createLedger(ensemble, writeQuorumSize,
                        ackQuorumSize,
                        BookKeeper.DigestType.CRC32,
                        passwd);
                LOG.debug("Ledger Handle: " + lh[i].getId());
            }
        } catch (BKException e) {
            e.printStackTrace();
        }
    }

    Random rand = new Random();
    public void close() throws InterruptedException, BKException {
        for(int i = 0; i < numberOfLedgers; i++) {
            lh[i].close();
        }
        bk.close();
    }

    long previous = 0;
    byte bytes[];

    void setEntryData(byte data[]) {
        bytes = data;
    }

    int lastLedger = 0;
    private int getRandomLedger() {
        return rand.nextInt(numberOfLedgers);
    }

    int latencyIndex = -1;
    AtomicLong completedRequests = new AtomicLong(0);

    long duration = -1;
    synchronized public long getDuration() {
        return duration;
    }

    public void run() {
        LOG.info("Running...");
        long start = previous = System.currentTimeMillis();

        int sent = 0;

        Thread reporter = new Thread() {
            public void run() {
                try {
                    while(true) {
                        Thread.sleep(1000);
                        LOG.info("ms: {} req: {}", System.currentTimeMillis(), completedRequests.getAndSet(0));
                    }
                } catch (InterruptedException ie) {
                    LOG.info("Caught interrupted exception, going away");
                }
            }
        };
        reporter.start();
        long beforeSend = System.nanoTime();

        while(!Thread.currentThread().isInterrupted() && sent < sendLimit) {
            try {
                sem.acquire();
                if (sent == 10000) {
                    long afterSend = System.nanoTime();
                    long time = afterSend - beforeSend;
                    LOG.info("Time to send first batch: {}s {}ns ",
                            time/1000/1000/1000, time);
                }
            } catch (InterruptedException e) {
                break;
            }

            final int index = getRandomLedger();
            LedgerHandle h = lh[index];
            if (h == null) {
                LOG.error("Handle " + index + " is null!");
            } else {
                long nanoTime = System.nanoTime();
                lh[index].asyncAddEntry(bytes, this, new Context(sent, nanoTime));
                counter.incrementAndGet();
            }
            sent++;
        }
        LOG.info("Sent: "  + sent);
        try {
            int i = 0;
            while(this.counter.get() > 0) {
                Thread.sleep(1000);
                i++;
                if (i > 30) {
                    break;
                }
            }
        } catch(InterruptedException e) {
            LOG.error("Interrupted while waiting", e);
        }
        synchronized(this) {
            duration = System.currentTimeMillis() - start;
        }
        throughput = sent*1000/getDuration();

        reporter.interrupt();
        try {
            reporter.join();
        } catch (InterruptedException ie) {
            // ignore
        }
        LOG.info("Finished processing in ms: " + getDuration() + " tp = " + throughput);
    }

    long throughput = -1;
    public long getThroughput() {
        return throughput;
    }

    long threshold = 20000;
    long runningAverageCounter = 0;
    long totalTime = 0;
    @Override
    public void addComplete(int rc, LedgerHandle lh, long entryId, Object ctx) {
        Context context = (Context) ctx;
        entryId = context.id;
        long newTime = System.nanoTime() - context.localStartTime;

        sem.release();
        counter.decrementAndGet();

        if (rc == 0) {
            latencies[(int)entryId] = newTime;
            completedRequests.incrementAndGet();
        }
    }

    // =========================================================================
    // REFACTORING START: Main e Helper Methods
    // =========================================================================

    public static void main(String[] args) throws Exception {
        Options options = buildOptions();
        CommandLine cmd = new PosixParser().parse(options, args);

        if (cmd.hasOption("help")) {
            new HelpFormatter().printHelp("BenchThroughputLatency <options>", options);
            System.exit(-1);
        }

        // 1. Setup Parametri (Parsing diretto per risparmiare righe)
        long durationMs = Long.parseLong(cmd.getOptionValue("time", "60")) * 1000;
        String servers = cmd.getOptionValue("zookeeper", "localhost:2181");
        int entrySize = Integer.parseInt(cmd.getOptionValue("entrysize", "1024"));
        int ledgers = Integer.parseInt(cmd.getOptionValue("ledgers", "1"));
        int throttle = Integer.parseInt(cmd.getOptionValue("throttle", "10000"));
        int sendLimit = Integer.parseInt(cmd.getOptionValue("sendlimit", "20000000"));
        byte[] passwd = cmd.getOptionValue("password", "benchPasswd").getBytes(UTF_8);
        ClientConfiguration conf = new ClientConfiguration().setThrottleValue(throttle).setZkServers(servers);

        setupTimeout(cmd); // Estratto timeout logic

        // 2. Warmup
        byte[] data = new byte[entrySize];
        Arrays.fill(data, (byte)'x');
        if (!cmd.hasOption("skipwarmup")) {
            LOG.info("Warmup tp: {}", warmUp(data, ledgers, Integer.parseInt(cmd.getOptionValue("ensemble", "3")),
                    Integer.parseInt(cmd.getOptionValue("quorum", "2")), passwd, conf));
        }

        // 3. Setup Benchmark
        BenchThroughputLatency bench = new BenchThroughputLatency(
                Integer.parseInt(cmd.getOptionValue("ensemble", "3")),
                Integer.parseInt(cmd.getOptionValue("quorum", "2")),
                Integer.parseInt(cmd.getOptionValue("ackQuorum", cmd.getOptionValue("quorum", "2"))),
                passwd, ledgers, sendLimit, conf);
        bench.setEntryData(data);

        // 4. Coordinamento ZK (Logica estratta per ridurre complessità nesting)
        waitForCoordination(servers, cmd.getOptionValue("coordnode"));

        // 5. Esecuzione
        Thread thread = new Thread(bench);
        thread.start();
        Thread.sleep(durationMs);
        thread.interrupt();
        thread.join();

        // 6. Calcolo e Reportistica (Logica IO e Math estratta)
        reportResults(bench, cmd.getOptionValue("latencyFile", "latencyDump.dat"), servers, cmd.getOptionValue("coordnode"));
        bench.close();
    }

    // --- Helper Methods (Mantine il codice pulito ma locale) ---

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption("time", true, "Running time (seconds), default 60");
        options.addOption("entrysize", true, "Entry size (bytes), default 1024");
        options.addOption("ensemble", true, "Ensemble size, default 3");
        options.addOption("quorum", true, "Quorum size, default 2");
        options.addOption("ackQuorum", true, "Ack quorum size, default is same as quorum");
        options.addOption("throttle", true, "Max outstanding requests, default 10000");
        options.addOption("ledgers", true, "Number of ledgers, default 1");
        options.addOption("zookeeper", true, "Zookeeper ensemble, default \"localhost:2181\"");
        options.addOption("password", true, "Password used to create ledgers (default 'benchPasswd')");
        options.addOption("coordnode", true, "Coordination znode for multi client benchmarks (optional)");
        options.addOption("timeout", true, "Number of seconds after which to give up");
        options.addOption("sockettimeout", true, "Socket timeout for bookkeeper client. In seconds. Default 5");
        options.addOption("skipwarmup", false, "Skip warm up, default false");
        options.addOption("sendlimit", true, "Max number of entries to send. Default 20000000");
        options.addOption("latencyFile", true, "File to dump latencies. Default is latencyDump.dat");
        options.addOption("help", false, "This message");
        return options;
    }

    private static void setupTimeout(CommandLine cmd) {
        if (cmd.hasOption("timeout")) {
            long timeout = Long.parseLong(cmd.getOptionValue("timeout", "360")) * 1000;
            new Timer().schedule(new TimerTask() {
                public void run() {
                    System.err.println("Timing out benchmark after " + timeout + "ms");
                    System.exit(-1);
                }
            }, timeout);
        }
    }

    private static void waitForCoordination(String servers, String coordNode) throws Exception {
        if (coordNode == null) return;

        CountDownLatch connectLatch = new CountDownLatch(1);
        ZooKeeper zk = new ZooKeeper(servers, 15000, e -> {
            if (e.getState() == KeeperState.SyncConnected) connectLatch.countDown();
        });

        if (!connectLatch.await(10, TimeUnit.SECONDS)) {
            zk.close();
            throw new IOException("Couldn't connect to zookeeper");
        }

        CountDownLatch nodeLatch = new CountDownLatch(1);
        if (zk.exists(coordNode, e -> {
            if (e.getType() == EventType.NodeCreated) nodeLatch.countDown();
        }) != null) {
            nodeLatch.countDown();
        }
        nodeLatch.await();
        LOG.info("Coordination znode created");
        zk.close();
    }

    private static void reportResults(BenchThroughputLatency bench, String latencyFile, String servers, String coordNode) throws Exception {
        // Uso Stream per ridurre i cicli manuali (riduce LOC e complessità cognitiva)
        long[] latencies = Arrays.stream(bench.latencies)
                .filter(l -> l > 0)
                .limit(bench.sendLimit)
                .sorted()
                .toArray();

        long duration = bench.getDuration();
        long tp = duration > 0 ? (latencies.length * 1000L) / duration : 0;

        LOG.info("{} completions in {} seconds: {} ops/sec", latencies.length, duration, tp);
        LOG.info("99th percentile latency: {}", percentile(latencies, 99));
        LOG.info("95th percentile latency: {}", percentile(latencies, 95));

        // Scrittura file ottimizzata
        try (PrintWriter writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(latencyFile)))) {
            for (long l : latencies) {
                writer.printf("%d\t%dms%n", l, l / 1000000);
            }
        }

        if (coordNode != null && servers != null) {
            ZooKeeper zk = new ZooKeeper(servers, 15000, null); // Quick connect for final write
            zk.create(coordNode + "/worker-", ("tp " + tp + " duration " + duration).getBytes(UTF_8),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
            zk.close();
        }
    }

    // --- FINE REFACTORING ---

    // Metodi esistenti (warmUp e percentile) mantenuti per retrocompatibilità interna
    private static double percentile(long[] latency, int percentile) {
        int size = latency.length;
        int sampleSize = (size * percentile) / 100;
        long total = 0;
        int count = 0;
        for(int i = 0; i < sampleSize; i++) {
            total += latency[i];
            count++;
        }
        return ((double)total/(double)count)/1000000.0;
    }

    private static long warmUp(byte[] data, int ledgers, int ensemble, int qSize,
                               byte[] passwd, ClientConfiguration conf)
            throws KeeperException, IOException, InterruptedException, BKException {
        final CountDownLatch connectLatch = new CountDownLatch(1);
        final int bookies;
        String bookieRegistrationPath = conf.getZkAvailableBookiesPath();
        ZooKeeper zk = null;
        try {
            final String servers = conf.getZkServers();
            zk = new ZooKeeper(servers, 15000, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getState() == KeeperState.SyncConnected) {
                        connectLatch.countDown();
                    }
                }});
            if (!connectLatch.await(10, TimeUnit.SECONDS)) {
                LOG.error("Couldn't connect to zookeeper at " + servers);
                throw new IOException("Couldn't connect to zookeeper " + servers);
            }
            bookies = zk.getChildren(bookieRegistrationPath, false).size();
        } finally {
            if (zk != null) {
                zk.close();
            }
        }

        BenchThroughputLatency warmup = new BenchThroughputLatency(bookies, bookies, bookies, passwd,
                ledgers, 10000, conf);
        warmup.setEntryData(data);
        Thread thread = new Thread(warmup);
        thread.start();
        thread.join();
        warmup.close();
        return warmup.getThroughput();
    }
}