package ch.so.agi.ilivalidator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import ch.interlis.iox.IoxException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;

import javax.websocket.OnError;
import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class WebSocketHandler extends AbstractWebSocketHandler {    
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static String FOLDER_PREFIX = "ilivalidator_";

    private static String LOG_ENDPOINT = "log";
    
    @Autowired
    IlivalidatorService ilivalidator;

    String filename;
    File file;
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {        
        filename = message.getPayload();
        
        // ilivalidator must know if it is ili1 or ili2.
        Path copiedFile = Paths.get(file.getParent(), filename);
        Files.copy(file.toPath(), copiedFile, StandardCopyOption.REPLACE_EXISTING);
        
        session.sendMessage(new TextMessage("Received: " + filename));
        
        String logFilename = copiedFile.toFile().getAbsolutePath() + ".log";
        log.info(logFilename);
        
        // There is no option for config file support in the GUI at the moment.
        String configFile = "on";

        // Run the validation.
        String allObjectsAccessible = "true";
        boolean valid;
        try {
            session.sendMessage(new TextMessage("Validating..."));
            valid = ilivalidator.validate(allObjectsAccessible, configFile, copiedFile.toFile().getAbsolutePath(), logFilename);
        } catch (IoxException | IOException e) {
            e.printStackTrace();            
            log.error(e.getMessage());
            
            TextMessage errorMessage = new TextMessage("An error occured while validating the data:" + e.getMessage());
            session.sendMessage(errorMessage);
            
            return;
        }

        String resultText = "<span style='color:green;'>...validation done:</span>";
        if (!valid) {
            resultText = "<span style='color:red;'>...validation failed:</span>";
        }
        
        String logFileId = copiedFile.getParent().getFileName().toString();
        TextMessage resultMessage = new TextMessage(resultText + " <a href='"+LOG_ENDPOINT+"/"+logFileId+"/"+filename+".log' target='_blank'>Download log file.</a>");
        session.sendMessage(resultMessage);
    }
    
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
        Path tmpDirectory = Files.createTempDirectory(FOLDER_PREFIX);
        
        // Ili1 muss itf als Endung haben, sonst wird falsch geprüft.
        Path uploadFilePath = Paths.get(tmpDirectory.toString(), "data.file"); 
                
        FileChannel fc = new FileOutputStream(uploadFilePath.toFile().getAbsoluteFile(), false).getChannel();
        fc.write(message.getPayload());
        fc.close();

        file = uploadFilePath.toFile();
    }
}