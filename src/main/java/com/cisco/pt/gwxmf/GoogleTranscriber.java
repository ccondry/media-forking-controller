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
 * SPEECH TO TEXT USING GOOGLE CLOUD
 * 
 * Handles streaming of media for recognition/transcription using Google Speech Service.
 *
 * -----------------------------------------------------------------------------------
 * 1.0,  Paul Tindall, Cisco, 13 Jul 2018 Initial version
 * -----------------------------------------------------------------------------------
 */

import static com.cisco.pt.gwxmf.MediaDirection.*;
import com.google.api.gax.rpc.BidiStream;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig;
import com.google.cloud.speech.v1p1beta1.SpeechClient;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1p1beta1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1p1beta1.StreamingRecognitionResult;
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeResponse;
import static com.google.cloud.speech.v1p1beta1.StreamingRecognizeResponse.SpeechEventType.*;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.text.DecimalFormat;
import org.json.JSONObject;


public class GoogleTranscriber {

    private final String DEFAULT_LANGUAGE = "en-US";
    private MediaListener cgrtp;
    private MediaListener cdrtp;
    private RecognitionConfig reccfg;
    private StreamingRecognitionConfig strcfg;


    public GoogleTranscriber(String addr) throws IOException, MediaForkingException {
        this(addr, null);
    }


    public GoogleTranscriber(String addr, String lang) throws IOException, MediaForkingException {
      // default single utterance = true
      // default automatic punctuation = true
      this(addr, lang, true, true);
    }

    public GoogleTranscriber(String addr, String lang, boolean singleUtterance) throws IOException, MediaForkingException {
      // default single utterance = true
      // default automatic punctuation = true
      this(addr, lang, singleUtterance, true);
    }

    public GoogleTranscriber(String addr, String lang, boolean singleUtterance, boolean automaticPunctuation) throws IOException, MediaForkingException {
        cgrtp = new MediaListener(addr);
        cdrtp = new MediaListener(addr);

        if (lang == null) {
            lang = DEFAULT_LANGUAGE;
        }
        
        reccfg = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.MULAW)
                    .setSampleRateHertz(8000)
                    .setLanguageCode(lang)
                    .setEnableAutomaticPunctuation(automaticPunctuation)
                    .build();

        strcfg = StreamingRecognitionConfig.newBuilder()
                    .setConfig(reccfg)
                    .setSingleUtterance(singleUtterance)
                    .build();
    }


    public int getPort(MediaDirection medir) {
        MediaListener rtp = CALLING.equals(medir) ? cgrtp : cdrtp;
        return rtp.getPort();
    }


    public void close() throws IOException {
        cgrtp.close();
        cdrtp.close();
    }


    public JSONObject transcribeCaller() throws IOException {
        return transcribe(CALLING);
    }


    public JSONObject transcribeCalled() throws IOException {
        return transcribe(CALLED);
    }


    public JSONObject transcribe(MediaDirection medir) throws IOException {

        MediaListener rtp = CALLING.equals(medir) ? cgrtp : cdrtp;
        JSONObject outcome = new JSONObject();
        
        try (SpeechClient speech = SpeechClient.create()) {
            BidiStream<StreamingRecognizeRequest, StreamingRecognizeResponse> stream = speech.streamingRecognizeCallable().call();
            StreamingRecognizeRequest cfgreq = StreamingRecognizeRequest.newBuilder().setStreamingConfig(strcfg).build();
            stream.send(cfgreq);

            rtp.start();
            rtp.processMedia((raw) -> {
                stream.send(StreamingRecognizeRequest.newBuilder().setAudioContent(ByteString.copyFrom(raw)).build());
            });

            for (StreamingRecognizeResponse rsp : stream) {

                System.out.println("===========================================================================================");
                System.out.println("Error: " + rsp.getError().getCode() + " " + rsp.getError().getMessage());
                System.out.println("Event: " + rsp.getSpeechEventType().toString());
                rsp.getResultsList().iterator().forEachRemaining(action -> {
                    System.out.println("Final: " + action.getIsFinal());
                    action.getAlternativesList().iterator().forEachRemaining(tr -> {
                        System.out.println("*******************************************************************************************");
                        System.out.println("*****");
                        System.out.println("*****    Transcript: " + tr.getTranscript());
                        System.out.println("*****    Confidence: " + tr.getConfidence());
                        System.out.println("*****");
                        System.out.println("*******************************************************************************************");
                    });
                });
                System.out.println("-------------------------------------------------------------------------------------------");

                if (rsp.getSpeechEventType().equals(END_OF_SINGLE_UTTERANCE)) {
                    rtp.discardMedia();
                    stream.closeSend();

                } else if (rsp.getError().getCode() != 0) {
                    outcome.put("error", rsp.getError().getMessage());
                    stream.cancel();                    

                } else {
                    StreamingRecognitionResult result = rsp.getResultsList().get(0);
                    if (result.getIsFinal()) {
                        SpeechRecognitionAlternative alt = result.getAlternatives(0);
                        outcome.put("transcript", alt.getTranscript())
                               .put("confidence", (new DecimalFormat("0.00")).format(alt.getConfidence()));
                        stream.cancel();
                    }
                }

            }

        } finally {
            rtp.discardMedia();
            rtp.stop();
        }

        return outcome;
    }
}
