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
 * TEXT TO SPEECH USING GOOGLE CLOUD
 *
 * Uses Google Speech Service TTS to generate WAV file and stream in response to
 * servlet requests.  The response content is delivered as type audio/x-wav.
 *
 * HTTP GET request URL:
 *
 *   http://<host:port/path>/tts/<lang>/<gender>/<codec>?text=TTS+text+escaped
 *
 * Path params are in accordance with the options Google TTS supports:
 *
 *   <lang>   Language locale, for example en-GB, es-ES, etc
 *   <gender> Specify female or male
 *   <codec>  Use ulaw or alaw
 *
 * The query param with ID text specifies the text message you want to convert into
 * speech.   As it's part of the URL, the text must be URI-encoded or in the case
 * of just simple test, plus (+) can be used instead of space characters.
 *
 * Directory structure should be created manually under the web app working directory
 * to cache the TTS wav files. Add directories for each combination of language,
 * gender and codec for which you will invoke TTS requests., for example:
 *
 *   .../tts/en-GB/FEMALE/ALAW
 *   .../tts/en-GB/MALE/ALAW
 *   .../tts/en-US/FEMALE/ULAW
 *   .../tts/en-US/MALE/ULAW
 *
 * (Note use of upper case for gender and codec)
 *
 * -----------------------------------------------------------------------------------
 * 1.0,  Paul Tindall, Cisco,  9 Feb 2019 Initial version
 * 1.1,  Paul Tindall, Cisco, 22 Feb 2019 Fix to prevent click at front of audio,
 *                                        removed WAV header before conversion to G.711
 * -----------------------------------------------------------------------------------
 */

import com.google.cloud.texttospeech.v1beta1.AudioConfig;
import com.google.cloud.texttospeech.v1beta1.AudioEncoding;
import com.google.cloud.texttospeech.v1beta1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1beta1.SynthesisInput;
import com.google.cloud.texttospeech.v1beta1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1beta1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1beta1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;


@WebServlet(name = "TextToSpeech", urlPatterns = {"/tts/*"})
public class TextToSpeech extends HttpServlet {

    static final String TTSMAPFILE = "tts/ttsmap.json";
    static final int WAVFILE_STANDARD_HDRLEN = 44;
    ConcurrentHashMap<String, String> ttsfile = new ConcurrentHashMap<>();

    protected void processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String[] pathitems;
        String text = req.getParameter("text");

        if (text == null ||
            req.getPathInfo() == null ||
            (pathitems = req.getPathInfo().split("/")).length < 4) {

            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid URL, missing mandatory fields");

        } else {
            System.out.println("\nRequest (" + req.getRequestURI() + ") from " + req.getRemoteAddr());
            System.out.println("TTS text = <" + text + ">\n");

            String lang = pathitems[1];
            String voice = pathitems[2].toUpperCase();
            String encoding = pathitems[3].toUpperCase();

            String key = lang + ":" + voice + ":" + encoding + ":" + text;
            String fna = ttsfile.get(key);

            if (fna == null) {
                try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
                    SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();

                    VoiceSelectionParams voicecfg = VoiceSelectionParams.newBuilder()
                            .setLanguageCode(lang)
                            .setSsmlGender(SsmlVoiceGender.valueOf(voice))
                            .build();

                    AudioConfig audiocfg = AudioConfig.newBuilder()
                            .setAudioEncoding(AudioEncoding.LINEAR16)
                            .setSampleRateHertz(8000)
                            .build();

                    SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voicecfg, audiocfg);

                    ByteString audioContents = response.getAudioContent();
                    ByteString audioPayload = audioContents.substring(WAVFILE_STANDARD_HDRLEN);
                    AudioFormat audinfmt = new AudioFormat(8000f, 16, 1, true, false);

                    AudioFormat.Encoding codec = "ULAW".equals(encoding) ? AudioFormat.Encoding.ULAW :
                                                 "ALAW".equals(encoding) ? AudioFormat.Encoding.ALAW : AudioFormat.Encoding.ULAW;

                    AudioInputStream audin = new AudioInputStream(audioPayload.newInput(), audinfmt, audioPayload.size() / 2);
                    AudioInputStream audout = AudioSystem.getAudioInputStream(codec, audin);
                    // construct file name using current date and time
                    SimpleDateFormat fmt = new SimpleDateFormat("TTS'_DDDHHmmssSSS'.wav");
                    String filename = fmt.format(new Date());
                    // construct folder path
                    String folderPath = "tts/" + lang + "/" + voice + "/" + encoding;
                    File folder = new File(folderPath);
                    // make sure the folder exists
                    folder.mkdirs();
                    // construct entire file path
                    String audfile = folder + filename;
                    // write the audio file to the filesystem
                    try (OutputStream out = new FileOutputStream(file)) {
                        int writelen = AudioSystem.write(audout, AudioFileFormat.Type.WAVE, out);
                        System.out.println(writelen + " bytes written to file " + audfile);
                        ttsfile.put(key, audfile);
                    }

                    audout.reset();
                    try (ServletOutputStream out = resp.getOutputStream()) {
                        resp.setContentType("audio/x-wav");
                        long now = System.currentTimeMillis();
                        resp.setDateHeader("Last-Modified", now);
                        resp.setDateHeader("Date", now);
                        AudioSystem.write(audout, AudioFileFormat.Type.WAVE, out);
                    }
                }

            } else {
                System.out.println(key + " ==> " + fna);
                File audin = new File(fna);

                try (FileInputStream audstream = new FileInputStream(audin)) {
                    try (ServletOutputStream out = resp.getOutputStream()) {
                        resp.setContentType("audio/x-wav");
                        resp.setDateHeader("Last-Modified", audin.lastModified());
                        resp.setDateHeader("Date", System.currentTimeMillis());

                        byte[] buf = new byte[1024];
                        int rdlen;

                        while ((rdlen = audstream.read(buf)) > 0) {
                            out.write(buf, 0, rdlen);
                        }
                    }
                }
            }
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
