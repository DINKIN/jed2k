package org.jed2k;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.InetSocketAddress;

import org.jed2k.alert.Alert;
import org.jed2k.alert.SearchResultAlert;
import org.jed2k.alert.ServerMessageAlert;
import org.jed2k.alert.ServerStatusAlert;
import org.jed2k.exception.JED2KException;
import org.jed2k.protocol.NetworkIdentifier;
import org.jed2k.protocol.server.SharedFileEntry;
import org.jed2k.protocol.server.search.SearchRequest;
import org.jed2k.protocol.server.search.SearchResult;
import org.jed2k.protocol.tag.Tag;

public class Conn {
    private static Logger log = Logger.getLogger(Conn.class.getName());
    private static SearchResult globalSearchRes = null;

    private static void printGlobalSearchResult() {
        if (globalSearchRes == null) return;
        int index = 0;
        for(SharedFileEntry entry: globalSearchRes.files) {
            System.out.println(String.format("%03d ", index++) + entry.toString());
        }
        System.out.println("More results: " + (globalSearchRes.hasMoreResults()?"yes":"no"));
    }

    public static void main(String[] args) throws IOException {
        Logger logger = Logger.getLogger("");
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        Handler[] handlers = logger.getHandlers();
        for(Handler handler : handlers) {
            handler.setLevel(Level.ALL);
            //logger.removeHandler(handler);
        }

        if (args.length < 1) {
            System.out.println("Specify incoming directory");
            return;
        }

        Path incomingDir = FileSystems.getDefault().getPath(args[0]);
        System.out.println("Incoming directory set to: " + incomingDir);

        System.out.println("Conn started");
        final Settings startSettings = new Settings();
        final Session s = new Session(startSettings);
        s.start();

        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

        ScheduledFuture scheduledFuture =
            scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                    Alert a = s.popAlert();
                    while(a != null) {
                        if (a instanceof SearchResultAlert) {
                            SearchResult sr = ((SearchResultAlert)a).results;
                            globalSearchRes = sr;
                            printGlobalSearchResult();
                        }
                        else if (a instanceof ServerMessageAlert) {
                            System.out.println("Server message: " + ((ServerMessageAlert)a).msg);
                        }
                        else if (a instanceof ServerStatusAlert) {
                            ServerStatusAlert ssa = (ServerStatusAlert)a;
                            System.out.println("Files count = " + ssa.filesCount + " users count = " + ssa.usersCount);
                        }
                        else {
                            System.out.println("Unknown alert received: " + a.toString());
                        }

                        a = s.popAlert();
                    }
                }
            },
        1, 1,
        TimeUnit.SECONDS);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String command;

        while ((command = in.readLine()) != null) {
            String[] parts = command.split("\\s+");

            if (parts[0].compareTo("exit") == 0 || parts[0].compareTo("quit") == 0) {
                s.interrupt();
                try {
                    s.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }

            if (parts[0].compareTo("listen") == 0 && parts.length == 2) {
            	Settings settings = new Settings();
            	settings.listenPort = (short)Integer.parseInt(parts[1]);
            	s.configureSession(settings);
            }
            if (parts[0].compareTo("connect") == 0 && parts.length >= 2) {
                s.connectoTo(new InetSocketAddress(parts[1], (short)Integer.parseInt((parts.length > 2)?parts[2]:"4661")));
            }
            else if (parts[0].compareTo("search") == 0 && parts.length > 1) {
                String searchExpression = command.substring("search".length());
                log.info("search expression:" + searchExpression);
                try {
                    log.info("search request: " + s);
                    s.search(SearchRequest.makeRequest(0, 0, 0, 0, "", "", "", 0, 0, searchExpression));
                } catch(JED2KException e) {
                    log.warning(e.getMessage());
                }
            } else if (parts[0].compareTo("peer") == 0 && parts.length == 3) {
                s.connectToPeer(new NetworkIdentifier(Integer.parseInt(parts[1]), (short)Integer.parseInt(parts[2])));
            } else if (parts[0].compareTo("load") == 0 && parts.length == 2) {
                int index = Integer.parseInt(parts[1]);
                if (index >= globalSearchRes.files.size() || index < 0) {
                    System.out.println("Specified index out of last search result bounds");
                } else {
                    SharedFileEntry sfe = globalSearchRes.files.get(index);
                    Path filepath = null;
                    long filesize = 0;
                    for(final Tag t: sfe.properties) {
                        if (t.id() == Tag.FT_FILESIZE) {
                            try {
                                filesize = t.longValue();
                            }catch(JED2KException e) {
                                System.out.println("Unable to extract filesize");
                            }
                        }

                        if (t.id() == Tag.FT_FILENAME) {
                            try {
                                filepath = Paths.get(args[0], t.stringValue());
                            } catch(JED2KException e) {
                                System.out.println("unable to extract filename");
                            }
                        }
                    }

                    if (filepath != null && filesize != 0) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Transfer ").append(filepath).append(" hash: ");
                        sb.append(sfe.hash.toString()).append(" size: ");
                        sb.append(filesize);
                        System.out.println(sb);
                        TransferHandle h = s.addTransfer(sfe.hash, filesize, filepath.toAbsolutePath().toString());
                        if (h.isValid()) {
                            System.out.println("transfer valid " + h.getHash());
                        }
                    }
                    else {
                        System.out.println("Not enough parameters to start new transfer");
                    }
                }
            }
            else if (parts[0].compareTo("save") == 0) {
                // saving search results to file for next usage
                if (globalSearchRes != null && !globalSearchRes.files.isEmpty()) {
                    ByteBuffer bb = ByteBuffer.allocate(globalSearchRes.bytesCount());
                    bb.order(ByteOrder.LITTLE_ENDIAN);
                    try {
                        globalSearchRes.put(bb);
                        bb.flip();
                        File f = new File(Paths.get(args[0], "search_results.txt").toString());
                        FileChannel channel = new FileOutputStream(f, false).getChannel();
                        channel.write(bb);
                        channel.close();
                    } catch(IOException e) {
                        System.out.println("I/O exception on save " + e);
                    } catch(JED2KException e) {
                        System.out.println("Unable to save search result: " + e);
                    }
                } else {
                    System.out.println("Won't save empty search result");
                }
            }
            else if (parts[0].compareTo("restore") == 0) {
                try {
                    File f = new File(Paths.get(args[0], "search_results.txt").toString());
                    ByteBuffer bb = ByteBuffer.allocate((int)f.length());
                    bb.order(ByteOrder.LITTLE_ENDIAN);
                    FileChannel channel = new  FileInputStream(f).getChannel();
                    channel.read(bb);
                    channel.close();
                    bb.flip();
                    globalSearchRes = new SearchResult();
                    globalSearchRes.get(bb);
                } catch(IOException e) {
                    System.out.println("I/O exception on load " + e);
                } catch(JED2KException e) {
                    System.out.println("Unable to load search results " + e);
                }
            }
            else if (parts[0].compareTo("print") == 0) {
                printGlobalSearchResult();
            }

        }

        scheduledExecutorService.shutdown();
        log.info("Conn finished");
    }
}