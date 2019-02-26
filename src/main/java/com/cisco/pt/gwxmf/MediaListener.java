package com.cisco.pt.gwxmf;

/*
 * ===================================================================================
 * IMPORTANT
 *
 * This sample is intended for distribution on Cisco DevNet. It does not form part of
 * the product release software and is not Cisco TAC supported. You should refer
 * to the Cisco DevNet website for the support rules that apply to samples published
 * for download.
 * ===================================================================================
 *
 * MEDIA LISTENING AND HANDLER
 * 
 * Handles asynchronous reading of forked RTP streams.  Currently assumes G.711 and
 * 20ms packetisation.
 *
 * -----------------------------------------------------------------------------------
 * 1.0,  Paul Tindall, Cisco, 13 Jul 2018 Initial version
 * -----------------------------------------------------------------------------------
 */

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MediaListener {

    static int RTPBASEPORT = 16384;
    static int RTPBUFLEN = 172;
    static int MAXBINDATTEMPTS = 16;
    private static int port = 0;

    private final DatagramChannel chn;
    private final ByteBuffer rxbuf;
    private final int rxport;
    private boolean active;
    private Consumer<byte[]> pkthandler;


    public MediaListener(String addr) throws IOException, MediaForkingException {

        int newport = 0;
        chn = DatagramChannel.open();

        for (int i = 0; i < MAXBINDATTEMPTS; i++) {
            newport = (RTPBASEPORT + port++) & 0xffff;
            try {
                chn.socket().bind(new InetSocketAddress(addr, newport));
                break;
            } catch (BindException ex) {
                newport = 0;
            }
        }
        
        if (newport == 0) {
            chn.close();
            throw new MediaForkingException("No available port for media stream");

        } else {
            rxport = newport;
            rxbuf = ByteBuffer.allocate(RTPBUFLEN);
        }
    }


    public void close() throws IOException {
        chn.close();
    }
    
    
    public void start() {
        if (!active) {
            readAsync();
            active = true;
        }
    }


    public void stop() {
        active = false;
    }


    public int getPort() {
        return rxport;
    }


    public void processMedia(Consumer<byte[]> handler) {
        pkthandler = handler;
    }


    public void discardMedia() {
        pkthandler = null;
    }


    private void readAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                SocketAddress client = chn.receive(rxbuf);
//                System.out.printf("Received %d bytes on port %d from %s%n",  rxbuf.position(), rxport, client);
            } catch (IOException ex) {
                System.out.println(ex);
            }

        }).thenRunAsync(() -> {
            if (pkthandler != null) {
                int pktlen = rxbuf.position();
                byte[] hdr = Arrays.copyOfRange(rxbuf.array(), 0, 12);
                byte[] payload = Arrays.copyOfRange(rxbuf.array(), 12, pktlen);
                long seq = (((int) hdr[2] & 0xff) << 8) + (((int) hdr[3]) & 0xff);
//                System.out.printf("Processing RTP packet on port %d, bytes = %d, sequence = %d%n", rxport, pktlen, seq);
                pkthandler.accept(payload);
            }
            rxbuf.clear();
            if (active) readAsync();
        });
    }
}
