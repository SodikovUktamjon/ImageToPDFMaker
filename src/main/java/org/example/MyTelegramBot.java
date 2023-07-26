package org.example;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import lombok.RequiredArgsConstructor;
import org.example.domains.User;
import org.example.repositories.UserRepository;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class MyTelegramBot extends TelegramLongPollingBot {
    private final static Map<String, List<String>> chatIdToPhotoPath=new HashMap<>();
    private final UserRepository userRepository;


    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();

        String chatId = String.valueOf(message.getChatId());
        if (message.hasPhoto()) {
            if(!userRepository.existsUserByUserId(chatId)){
                sendMessage(chatId, "You haven't registered by yet! Press /start to register");
                return;
            }
            String path;
            List<PhotoSize> photos = update.getMessage().getPhoto();
            PhotoSize photo = photos.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);
            if (photo != null) {
                try {
                    path = (downloadPhoto(photo).getPath());
                } catch (TelegramApiException | IOException e) {
                    e.printStackTrace();
                    return;
                }
                List<String> strings;

                if (chatIdToPhotoPath.containsKey(chatId)) {
                    strings = chatIdToPhotoPath.get(chatId);
                } else {
                    chatIdToPhotoPath.put(chatId, new ArrayList<>());
                    strings = new ArrayList<>();
                }
                strings.add(path);
                chatIdToPhotoPath.put(chatId, strings);


                SendMessage sendMessage = new SendMessage(String.valueOf(chatId), "Uploaded!");
                KeyboardRow r = new KeyboardRow();
                KeyboardButton b = new KeyboardButton("Generate");
                r.add(b);
                ReplyKeyboardMarkup replyMarkup = new ReplyKeyboardMarkup(List.of(r));
                replyMarkup.setResizeKeyboard(true);
                sendMessage.setReplyMarkup(replyMarkup);
                sendMessage.setChatId(String.valueOf(chatId));
                try {
                    execute(sendMessage);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        else if(message.getText().equals("/start")) {
            SendMessage sendMessage=new SendMessage(String.valueOf(chatId),"Hello, "+update.getMessage().getFrom().getFirstName()+"! Now we know you and you can upload photos and generate pdf files!");
            CompletableFuture.runAsync(()->{
                org.telegram.telegrambots.meta.api.objects.User from = message.getFrom();
               User user = User.builder()
                       .firstName(from.getFirstName())
                       .lastName(from.getLastName())
                       .username(from.getUserName())
                       .chatId(chatId)
                .build();
               userRepository.save(user);
            });
            try {
                execute(sendMessage);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }
        else if(message.getText().equals("Generate")){
            deleteMessage(message,chatId);
            List<String> strings = chatIdToPhotoPath.get(chatId);
            if(!userRepository.existsUserByUserId(chatId)){
                sendMessage(chatId, "You haven't registered by yet! Press /start and share your contact to register");
                return;
            }
            if(strings == null || strings.isEmpty()) {
                sendMessage(String.valueOf(chatId), "You have no photos to generate PDF!");
            }
            CompletableFuture.runAsync(()->{
                List<String> strings1 = chatIdToPhotoPath.get(chatId);
                File file = writePDFFile(strings1);
                sendPdfToUser(chatId, file);
                for(int i=0;i<chatIdToPhotoPath.get(chatId).size();i++){
                    new File(chatIdToPhotoPath.get(chatId).get(i)).delete();
                }
                chatIdToPhotoPath.remove(chatId);
                new File(file.getPath()).delete();
            });




        }
        else{
            deleteMessage(message,chatId);
            sendMessage(chatId,"Please upload your photos!");
        }


        }


    private void deleteMessage(Message message, String chatId) {
        DeleteMessage deleteMessage= new DeleteMessage();
        deleteMessage.setChatId(String.valueOf(chatId));
        deleteMessage.setMessageId(message.getMessageId());
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private File writePDFFile(List<String> imagePath) {
        String pdfPath = "src/main/resources/converted.pdf"; // Replace this with the desired output path for the PDF file
        Document document;
        try (PdfDocument pdfDocument = new PdfDocument(new PdfWriter(new FileOutputStream(pdfPath)))) {
            document = new Document(pdfDocument);
            for(String path:imagePath){
                ImageData imageData = ImageDataFactory.create(path);
                Image pdfImage = new Image(imageData);
                document.add(pdfImage);
            }

        } catch (IOException e) {
            System.err.println("Error occurred while processing the image or creating the PDF: " + e.getMessage());
        }
            return new File(pdfPath);

    }


    private File downloadPhoto(PhotoSize photo) throws TelegramApiException, IOException {
        GetFile getFile = new GetFile();
        getFile.setFileId(photo.getFileId());
        org.telegram.telegrambots.meta.api.objects.File telegramFile = execute(getFile);
        String fileUri = telegramFile.getFilePath();

        URL fileUrl = new URL("https://api.telegram.org/file/bot" + TelegramConfig.botToken + "/" + fileUri);
        String filePath = getFilePath(photo);

        Path directory = Paths.get(filePath).getParent();
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }

        File downloadedFile = new File(filePath);

        try (InputStream inputStream = fileUrl.openStream();
             FileOutputStream outputStream = new FileOutputStream(downloadedFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        return downloadedFile;
    }

    private String getFilePath(PhotoSize photo) {
        return String.format("src/main/resources/photos/%s.jpg", photo.getFileId());
    }


    private void sendPdfToUser(String chatId, File pdfFile) {
        if (pdfFile != null && pdfFile.exists()) {
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId);
            sendDocument.setDocument(new InputFile(pdfFile));
            try {
                execute(sendDocument);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }
    private void sendMessage(String chatId, String message)  {
        try {
            execute(new SendMessage(chatId, message));
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getBotUsername() {
        return "https://t.me/FileReturner_bot";
    }


}