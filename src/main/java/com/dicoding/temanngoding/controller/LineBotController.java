
package com.dicoding.temanngoding.controller;

import com.dicoding.temanngoding.database.Dao;
import com.dicoding.temanngoding.model.Event;
import com.dicoding.temanngoding.model.JoinEvent;
import com.dicoding.temanngoding.model.Payload;
import com.dicoding.temanngoding.model.User;
import com.google.gson.Gson;
import com.linecorp.bot.client.LineMessagingServiceBuilder;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.model.Multicast;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.URIAction;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.CarouselColumn;
import com.linecorp.bot.model.message.template.CarouselTemplate;
import com.linecorp.bot.model.profile.UserProfileResponse;
import com.linecorp.bot.model.response.BotApiResponse;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import retrofit2.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@RestController
@RequestMapping(value="/linebot")
public class LineBotController
{
    //inisialisasi channel secret
    @Autowired
    @Qualifier("com.linecorp.channel_secret")
    String lChannelSecret;

    //inisialisasi channel access token
    @Autowired
    @Qualifier("com.linecorp.channel_access_token")
    String lChannelAccessToken;

    @Autowired
    Dao mDao;

    private String displayName;
    private Payload payload;
    private String jObjGet = " ";


    @RequestMapping(value="/callback", method=RequestMethod.POST)
    public ResponseEntity<String> callback(
        @RequestHeader("X-Line-Signature") String aXLineSignature,
        @RequestBody String aPayload)
    {
         // compose body
        final String text=String.format("The Signature is: %s",
            (aXLineSignature!=null && aXLineSignature.length() > 0) ? aXLineSignature : "N/A");
        
        System.out.println(text);
        
        final boolean valid=new LineSignatureValidator(lChannelSecret.getBytes()).validateSignature(aPayload.getBytes(), aXLineSignature);
        
        System.out.println("The signature is: " + (valid ? "valid" : "tidak valid"));
        
        //Get events from source
        if(aPayload!=null && aPayload.length() > 0)
        {
            System.out.println("Payload: " + aPayload);
        }
        
        Gson gson = new Gson();
        payload = gson.fromJson(aPayload, Payload.class);
        
        //Variable initialization
        String msgText = " ";
        String idTarget = " ";
        String eventType = payload.events[0].type;
        
        //Get event's type
        if (eventType.equals("join")){
            if (payload.events[0].source.type.equals("group")){
                replyToUser(payload.events[0].replyToken, "Hello Group");
            }
            if (payload.events[0].source.type.equals("room")){
                replyToUser(payload.events[0].replyToken, "Hello Room");
            }
        } else if (eventType.equals("follow")){
            greetingMessage();
        }
        else if (eventType.equals("message")){    //Event's type is message
            if (payload.events[0].source.type.equals("group")){
                idTarget = payload.events[0].source.groupId;
            } else if (payload.events[0].source.type.equals("room")){
                idTarget = payload.events[0].source.roomId;
            } else if (payload.events[0].source.type.equals("user")){
                idTarget = payload.events[0].source.userId;
            }
            
            //Parsing message from user
            if (!payload.events[0].message.type.equals("text")){
                greetingMessage();
            } else {

                msgText = payload.events[0].message.text;
                msgText = msgText.toLowerCase();
                
                if (!msgText.contains("bot leave")){
                    if (msgText.contains("id") || msgText.contains("find") || msgText.contains("join")|| msgText.contains("teman")){
                        processText(payload.events[0].replyToken, idTarget, msgText);
                    } else {
                        try {
                            getEventData(msgText, payload, idTarget);
                        } catch (IOException e) {
                            System.out.println("Exception is raised ");
                            e.printStackTrace();
                        }
                    }
                } else {
                    if (payload.events[0].source.type.equals("group")){
                        leaveGR(payload.events[0].source.groupId, "group");
                    } else if (payload.events[0].source.type.equals("room")){
                        leaveGR(payload.events[0].source.roomId, "room");
                    }
                }

            }
        }
         
        return new ResponseEntity<String>(HttpStatus.OK);
    }

    //method untuk mengirim pesan saat ada user menambahkan bot sebagai teman
    private void greetingMessage(){
        getUserProfile(payload.events[0].source.userId);
        String greetingMsg =
                "Hi " + displayName + "! Pengen datang ke event developer tapi males sendirian? Aku bisa mencarikan kamu pasangan.";
        String action = "Lihat daftar event";
        String title = "Welcome";
        buttonTemplate(greetingMsg, action, action, title);

    }

    //method untuk mengirimkan pesan ke semua teman
    private void multicastMsg(String eventID, String userID){
        List<String> listId = new ArrayList<>();
        List<JoinEvent> self=mDao.getByEventId("%"+eventID+"%");
        if(self.size() > 0)
        {
            for (int i=0; i<self.size(); i++){
                listId.add(self.get(i).user_id);
                listId.remove(userID);
            }
        }
        System.out.println(listId);
        String msg = "Hi, ada teman baru telah bergabung di event "+eventID;
        Set<String> stringSet = new HashSet<String>( listId );
        ButtonsTemplate buttonsTemplate = new ButtonsTemplate(null, null, msg,
                Collections.singletonList(new MessageAction("Lihat Teman", "teman #"+eventID)));
        TemplateMessage templateMessage = new TemplateMessage("List Teman", buttonsTemplate);
        Multicast multicast = new Multicast(stringSet, templateMessage);
        try {
            Response<BotApiResponse> response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .multicast(multicast)
                    .execute();
            System.out.println(response.code() + " " + response.message());
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
    }

    //method untuk membuat button template
    private void buttonTemplate(String message, String label, String action, String title){
        ButtonsTemplate buttonsTemplate = new ButtonsTemplate(null, null, message,
                Collections.singletonList(new MessageAction(label, action)));
        TemplateMessage templateMessage = new TemplateMessage(title, buttonsTemplate);
        PushMessage pushMessage = new PushMessage(payload.events[0].source.userId, templateMessage);
        try {
            Response<BotApiResponse> response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .pushMessage(pushMessage)
                    .execute();
            System.out.println(response.code() + " " + response.message());
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
    }

    //method untuk memanggil dicoding event open api
    private void getEventData(String userTxt, Payload ePayload, String targetID) throws IOException{

        // Act as client with GET method
        String URI = "https://www.dicoding.com/public/api/events?limit=5";
        System.out.println("URI: " +  URI);

        CloseableHttpAsyncClient c = HttpAsyncClients.createDefault();
        
        try{
            c.start();
            //Use HTTP Get to retrieve data
            HttpGet get = new HttpGet(URI);
            
            Future<HttpResponse> future = c.execute(get, null);
            HttpResponse responseGet = future.get();
            System.out.println("HTTP executed");
            System.out.println("HTTP Status of response: " + responseGet.getStatusLine().getStatusCode());
            
            // Get the response from the GET request
            BufferedReader brd = new BufferedReader(new InputStreamReader(responseGet.getEntity().getContent()));
            
            StringBuffer resultGet = new StringBuffer();
            String lineGet = "";
            while ((lineGet = brd.readLine()) != null) {
                resultGet.append(lineGet);
            }
            System.out.println("Got result");
            
            // Change type of resultGet to JSONObject
            jObjGet = resultGet.toString();
            System.out.println("Event responses: " + jObjGet);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        } finally {
            c.close();
        }
        
        Gson mGson = new Gson();
        Event event = mGson.fromJson(jObjGet, Event.class);

            if (userTxt.equals("lihat daftar event")){
                pushMessage(targetID, "Aku akan mencarikan event aktif di dicoding! Dengan syarat : Kasih tau dong LINE ID kamu (pake \'id @\' ya)");
                pushMessage(targetID, "Contoh : id @john");
            }
            else if (userTxt.contains("summary")){
                pushMessage(targetID, event.getData().get(Integer.parseInt(String.valueOf(userTxt.charAt(1)))-1).getSummary());
            } else if (userTxt.contains("tampilkan")){
                carouselTemplateMessage(ePayload.events[0].source.userId);
            } else {
                pushMessage(targetID, "Hi "+displayName+", aku belum  mengerti maksud kamu. Silahkan ikuti petunjuk ya :)");
                greetingMessage();
            }
    }

    //Method untuk reply message
    private void replyToUser(String rToken, String messageToUser){
        TextMessage textMessage = new TextMessage(messageToUser);
        ReplyMessage replyMessage = new ReplyMessage(rToken, textMessage);
        try {
            Response<BotApiResponse> response = LineMessagingServiceBuilder
                .create(lChannelAccessToken)
                .build()
                .replyMessage(replyMessage)
                .execute();
            System.out.println("Reply Message: " + response.code() + " " + response.message());
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
    }

    //method untuk mendapatkan profile user (user id, display name, image, status)
    private void getUserProfile(String userId){
        Response<UserProfileResponse> response =
                null;
        try {
            response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .getProfile(userId)
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (response.isSuccessful()) {
            UserProfileResponse profile = response.body();
            System.out.println(profile.getDisplayName());
            System.out.println(profile.getPictureUrl());
            System.out.println(profile.getStatusMessage());
            displayName = profile.getDisplayName();
        } else {
            System.out.println(response.code() + " " + response.message());
        }
    }

    //Method untuk push message
    private void pushMessage(String sourceId, String txt){
        TextMessage textMessage = new TextMessage(txt);
        PushMessage pushMessage = new PushMessage(sourceId,textMessage);
        try {
            Response<BotApiResponse> response = LineMessagingServiceBuilder
            .create(lChannelAccessToken)
            .build()
            .pushMessage(pushMessage)
            .execute();
            System.out.println(response.code() + " " + response.message());
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
    }

    //method untuk template message berupa carousel
    private void carouselTemplateMessage(String sourceId){
        Gson mGson = new Gson();
        Event event = mGson.fromJson(jObjGet, Event.class);
        
        CarouselColumn carouselColumn = null;
        int i;
        for (i = 0; i<=event.getData().size(); i++){
            carouselColumn = new CarouselColumn
                    (event.getData().get(i).getImage_path(), event.getData().get(i).getOwner_display_name(),
                            event.getData().get(i).getName().substring(0, (event.getData().get(i).getName().length() < 60)?event.getData().get(i).getName().length():60),Arrays.asList
                            (new MessageAction("Summary", "["+String.valueOf(1)+"]"+" Summary : " + event.getData().get(i).getName()),
                                    new URIAction("View Page", event.getData().get(i).getLink()),
                                    new MessageAction("Join Event", "join event #"+event.getData().get(i).getId())));
        }
                
        
        CarouselTemplate carouselTemplateNew = new CarouselTemplate(Arrays.asList(carouselColumn));
        CarouselTemplate carouselTemplate = new CarouselTemplate(
                Arrays.asList(
                        new CarouselColumn
                                (event.getData().get(0).getImage_path(), event.getData().get(0).getOwner_display_name(),
                                        event.getData().get(0).getName().substring(0, (event.getData().get(0).getName().length() < 60)?event.getData().get(0).getName().length():60),Arrays.asList
                                        (new MessageAction("Summary", "["+String.valueOf(1)+"]"+" Summary : " + event.getData().get(0).getName()),
                                                new URIAction("View Page", event.getData().get(0).getLink()),
                                                new MessageAction("Join Event", "join event #"+event.getData().get(0).getId()))),
                        new CarouselColumn
                                (event.getData().get(1).getImage_path(), event.getData().get(1).getOwner_display_name(),
                                        event.getData().get(1).getName().substring(0, (event.getData().get(1).getName().length() < 60)?event.getData().get(1).getName().length():60),Arrays.asList
                                        (new MessageAction("Summary", "["+String.valueOf(2)+"]"+" Summary : " + event.getData().get(1).getName()),
                                                new URIAction("View Page", event.getData().get(1).getLink()),
                                                new MessageAction("Join Event", "join event #"+event.getData().get(1).getId()))),
                        new CarouselColumn
                                (event.getData().get(2).getImage_path(), event.getData().get(2).getOwner_display_name(),
                                        event.getData().get(2).getName().substring(0, (event.getData().get(2).getName().length() < 60)?event.getData().get(2).getName().length():60), Arrays.asList
                                        (new MessageAction("Summary", "["+String.valueOf(3)+"]"+" Summary : " + event.getData().get(2).getName()),
                                                new URIAction("View Page", event.getData().get(2).getLink()),
                                                new MessageAction("Join Event", "join event #"+event.getData().get(2).getId()))),
                        new CarouselColumn
                                (event.getData().get(3).getImage_path(), event.getData().get(3).getOwner_display_name(),
                                        event.getData().get(3).getName().substring(0, (event.getData().get(3).getName().length() < 60)?event.getData().get(3).getName().length():60), Arrays.asList
                                        (new MessageAction("Summary", "["+String.valueOf(4)+"]"+" Summary : " + event.getData().get(3).getName()),
                                                new URIAction("View Page", event.getData().get(3).getLink()),
                                                new MessageAction("Join Event", "join event #"+event.getData().get(3).getId())))));

        TemplateMessage templateMessage = new TemplateMessage("Your search result", carouselTemplateNew);
        PushMessage pushMessage = new PushMessage(sourceId,templateMessage);
        try {
            Response<BotApiResponse> response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .pushMessage(pushMessage)
                    .execute();
            System.out.println(response.code() + " " + response.message());
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
    }

    //Method for leave group or room
    private void leaveGR(String id, String type){
        try {
            if (type.equals("group")){
                Response<BotApiResponse> response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .leaveGroup(id)
                    .execute();
                System.out.println(response.code() + " " + response.message());
            } else if (type.equals("room")){
                Response<BotApiResponse> response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .leaveRoom(id)
                    .execute();
                System.out.println(response.code() + " " + response.message());
            }
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
    }

    //method yang berisi keyword dan trigger yang berhubungan dengan database
    private void processText(String aReplyToken, String aUserId, String aText)
    {
        System.out.println("message text: " + aText + " from: " + aUserId);

        String [] words=aText.trim().split("\\s+");
        String intent=words[0];
        System.out.println("intent: " + intent);
        String msg = " ";

        String lineId = " ";
        String eventId = " ";

        if(intent.equalsIgnoreCase("id"))
        {
            String target=words.length>1 ? words[1] : "";
            if (target.length()<=3)
            {
                msg = "Need more than 3 character to find user";
            }
            else
            {
                lineId = aText.substring(aText.indexOf("@") + 1);
                getUserProfile(payload.events[0].source.userId);
                String status = regLineID(aUserId, lineId, displayName);
                String message = status+"\nHi, berikut adalah event aktif yang bisa kamu pilih";
                buttonTemplate(message, "Tampilkan", "Tampilkan", "Daftar Event");

                return;
            }
        }
        else if (intent.equalsIgnoreCase("join")){
            eventId = aText.substring(aText.indexOf("#") + 1);
            getUserProfile(payload.events[0].source.userId);
            lineId = findUser(aUserId);
            joinEvent(eventId, aUserId, lineId, displayName );
            return;
        }

        else if (intent.equalsIgnoreCase("teman")){
            eventId = aText.substring(aText.indexOf("#") + 1);
            String txtMessage = findEvent(eventId);
            replyToUser(aReplyToken, txtMessage);
            return;
        }

        // if msg is invalid
        if(msg == " ")
        {
            replyToUser(aReplyToken, "Message invalid");
        }
    }

    //method mendaftarkan LINE ID
    private String regLineID(String aUserId, String aLineId, String aDisplayName){
        String regStatus;
        String exist = findUser(aUserId);
        if(exist=="User not found")
        {
            int reg=mDao.registerLineId(aUserId, aLineId, aDisplayName);
            if(reg==1)
            {
                regStatus="Yay berhasil mendaftar!";
            }
            else
            {
                regStatus="yah gagal mendaftar :(";
            }
        }
        else
        {
            regStatus="Anda sudah terdaftar";
        }

        return regStatus;
    }

    //method untuk mencari user terdaftar di database
    private String findUser(String aUSerId){
        String txt="";
        List<User> self=mDao.getByUserId("%"+aUSerId+"%");
        if(self.size() > 0)
        {
            for (int i=0; i<self.size(); i++){
                User user=self.get(i);
                txt=getUserString(user);
            }

        }
        else
        {
            txt="User not found";
        }
        return txt;
    }

    private String getUserString(User user)
    {
        return user.line_id;
    }

    //method untuk bergabung dalam event
    private void joinEvent(String eventID, String aUserId, String lineID, String aDisplayName){
        String joinStatus;
        String exist = findFriend(eventID, aUserId);
        if(Objects.equals(exist, "Event not found"))
        {
            int join =mDao.joinEvent(eventID, aUserId, lineID, aDisplayName);
            if(join ==1)
            {
                joinStatus="Kamu berhasil bergabung pada event ini. Berikut adalah beberapa teman yang bisa menemani kamu. Silahkan invite LINE ID berikut menjadi teman di LINE kamu ya :)";
                buttonTemplate(joinStatus, "Lihat Teman","teman #"+eventID, "List Teman");
                multicastMsg(eventID, aUserId);
            }
            else
            {
                pushMessage(aUserId, "yah gagal bergabung :(");
            }
        }
        else
        {
            buttonTemplate("Anda sudah tergabung di event ini", "Lihat Teman","teman #"+eventID, "List Teman");
        }

    }

    //method untuk mencari data di table event berdasarkan event id
    private String findEvent(String eventID){
        String txt="Daftar teman di event "+eventID+" :";
        List<JoinEvent> self=mDao.getByEventId("%"+eventID+"%");
        if(self.size() > 0)
        {
            for (int i=0; i<self.size(); i++){
                JoinEvent joinEvent=self.get(i);
                txt=txt+"\n\n";
                txt=txt+getEventString(joinEvent);
            }

        }
        else
        {
            txt="Event not found";
        }
        return txt;
    }

    //method untuk melihat teman terdaftar di dalam suatu event
    private String findFriend(String eventID, String  userID){
        String txt="Daftar teman di event "+eventID+" :";
        List<JoinEvent> self=mDao.getByJoin(eventID, userID);
        if(self.size() > 0)
        {
            for (int i=0; i<self.size(); i++){
                JoinEvent joinEvent=self.get(i);
                txt=txt+"\n\n";
                txt=txt+getEventString(joinEvent);
            }

        }
        else
        {
            txt="Event not found";
        }
        return txt;
    }

    private String getEventString(JoinEvent joinEvent)
    {
        return String.format("Display Name: %s\nLINE ID: %s\n", joinEvent.display_name, joinEvent.line_id);
    }

}
