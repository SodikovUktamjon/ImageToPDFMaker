package org.example;

import lombok.RequiredArgsConstructor;
import org.example.repositories.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@RequiredArgsConstructor
public class TelegramConfig {



    public static final String botToken="5688958276:AAHPSl1hIPLfMyWotTa_8tGMCGPEPugJWVo";


    @Bean
    public MyTelegramBot bot(UserRepository userRepository) throws TelegramApiException {
        MyTelegramBot bot = new MyTelegramBot(userRepository);
        TelegramBotsApi botsApi;
        botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
        return bot;
    }

}
