
package com.dicoding.temanngoding;

import com.google.gson.Gson;
import com.linecorp.bot.client.LineMessagingServiceBuilder;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.URIAction;
import com.linecorp.bot.model.message.ImageMessage;
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
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import retrofit2.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@RestController
@RequestMapping(value="/linebot")
public class LineBotController
{
    @Autowired
    @Qualifier("com.linecorp.channel_secret")
    String lChannelSecret;
    
    @Autowired
    @Qualifier("com.linecorp.channel_access_token")
    String lChannelAccessToken;

    private String displayName;
    private Payload payload;


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
                    try {
                        getEventData(msgText, payload, idTarget);
                    } catch (IOException e) {
                        System.out.println("Exception is raised ");
                        e.printStackTrace();
                    }
                } else {
                    if (payload.events[0].source.type.equals("group")){
                        leaveGR(payload.events[0].source.groupId, "group");
                    } else if (payload.events[0].source.type.equals("room")){
                        leaveGR(payload.events[0].source.roomId, "room");
                    }
                }
                
//                pushType(idTarget, msgText + " - " + payload.events[0].source.type);
            }
        }
         
        return new ResponseEntity<String>(HttpStatus.OK);
    }

    private void greetingMessage(){
        getUserProfile(payload.events[0].source.userId);
        String greetingMsg =
                "Hi " + displayName + "! Pengen datang ke event developer tapi males sendirian? Aku bisa mencarikan kamu pasangan.";
        ButtonsTemplate buttonsTemplate = new ButtonsTemplate(null, null, greetingMsg,
                Collections.singletonList(new MessageAction("Lihat daftar event", "event")));
        TemplateMessage templateMessage = new TemplateMessage("Welcome", buttonsTemplate);
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

    private void getEventData(String userTxt, Payload ePayload, String targetID) throws IOException{

//        if (title.indexOf("\"") == -1){
//            replyToUser(ePayload.events[0].replyToken, "Unknown keyword");
//            return;
//        }
//
//        title = title.substring(title.indexOf("\"") + 1, title.lastIndexOf("\""));
//        System.out.println("Index: " + Integer.toString(title.indexOf("\"")));
//        title = title.replace(" ", "+");
//        System.out.println("Text from User: " + title);

        // Act as client with GET method
        String URI = "https://www.dicoding.com/public/api/events";
        System.out.println("URI: " +  URI);
        
        String jObjGet = " ";
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
            System.out.println("OMDb responses: " + jObjGet);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        } finally {
            c.close();
        }
        
        Gson mGson = new Gson();
        Event event = mGson.fromJson(jObjGet, Event.class);

        if (userTxt.contains("summary 0")){
            pushMessage(targetID, event.getData().get(0).getSummary());
        } else if (userTxt.equals("summary 1")){
            pushMessage(targetID, html2text(event.getData().get(1).getSummary()).replaceAll("\\<.*?>",""));
        } else if (userTxt.equals("description")){
            pushMessage(targetID, html2text(event.getData().get(0).getDescription()).replaceAll("\\<.*?>",""));
        } else if (userTxt.equals("quota")){
            pushMessage(targetID, String.valueOf(event.getData().get(0).getQuota()));
        } else if (userTxt.equals("registrants")){
            pushMessage(targetID, String.valueOf(event.getData().get(0).getRegistrants()));
        } else if (userTxt.equals("address")){
            pushMessage(targetID, html2text(event.getData().get(0).getAddress()).replaceAll("\\<.*?>",""));
        }

        int position;
        for (position = 0; position<= event.getData().size(); position++){
            String name = event.getData().get(position).getName();
            int maxLength = (name.length() < 60)?name.length():60;
            name = name.substring(0, maxLength);
            String owner = event.getData().get(position).getOwner_display_name();
            String link = event.getData().get(position).getLink();
            String image = event.getData().get(position).getImage_path();
            if (userTxt.equals("event")) {
                carouselForUser(image, ePayload.events[0].source.userId, owner, name, link, position);
            }
        }

//        //Check whether response successfully retrieve or not
//        if (msgToUser.length() <= 11 || !ePayload.events[0].message.type.equals("text")){
//            replyToUser(ePayload.events[0].replyToken, "Request Timeout");
//        } else {
//            replyToUser(ePayload.events[0].replyToken, msgToUser);
//        }
    }

    public static String html2text(String html) {
        return Jsoup.parse(html).text();
    }

    //Method for reply user's message
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
    
    //Method for send movie's poster to user
    private void pushPoster(String sourceId, String poster_url){
        ImageMessage imageMessage = new ImageMessage(poster_url, poster_url);
        PushMessage pushMessage = new PushMessage(sourceId,imageMessage);
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
    
    //Method for push message to user
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
    
    //Method for send caraousel template message to user
    private void carouselForUser(String poster_url, String sourceId, String owner, String name, String uri, int position){

        CarouselTemplate carouselTemplate = new CarouselTemplate(
                    Arrays.asList(new CarouselColumn
                                    (poster_url, owner, name, Arrays.asList
                                        (new MessageAction("Summary", "summary " + position),
                                         new MessageAction("Description", "description : " ),
                                         new URIAction("Join Event", uri))),
                                    new CarouselColumn
                                    (poster_url, owner, name, Arrays.asList
                                            (new MessageAction("Quota", "quota : "),
                                                    new MessageAction("Registrants", "registrants : " ),
                                                    new MessageAction("Address", "address : " )))));

        TemplateMessage templateMessage = new TemplateMessage("List event", carouselTemplate);
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
}
