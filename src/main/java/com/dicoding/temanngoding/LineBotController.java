
package com.dicoding.temanngoding;

import com.dicoding.temanngoding.dao.UserDao;
import com.dicoding.temanngoding.model.Event;
import com.dicoding.temanngoding.model.Payload;
import com.dicoding.temanngoding.model.User;
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
import java.util.List;
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

    @Autowired
    UserDao mDao;

    private String displayName;
    private Payload payload;;


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
                        processText(payload.events[0].replyToken, idTarget, msgText);
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
                Collections.singletonList(new MessageAction("Lihat daftar event", "Lihat daftar event")));
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
        int position;
        for (position = 0; position<= event.getData().size(); position++){
            String name = event.getData().get(position).getName();
            String owner = event.getData().get(position).getOwner_display_name();
            String link = event.getData().get(position).getLink();
            String image = event.getData().get(position).getImage_path();

            if (userTxt.equals("event")) {
                carouselForUser(image, ePayload.events[0].source.userId, owner, name, link, position);
            } else if (userTxt.equals("Lihat daftar event")){
                pushMessage(targetID, "Aku akan mencarikan event aktif di dicoding! Dengan syarat : Kasih tau dong LINE ID kamu :) Contoh : id \"john\"");
            }
            else if (userTxt.contains("summary")){
                pushMessage(targetID, event.getData().get(Integer.parseInt(String.valueOf(userTxt.charAt(1)))-1).getSummary());
                break;
            } else if (userTxt.contains("description")){
                pushMessage(targetID, html2text(event.getData().get(Integer.parseInt(String.valueOf(userTxt.charAt(1)))-1).getDescription()).replaceAll("\\<.*?>",""));
                break;
            } else if (userTxt.contains("quota")){
                pushMessage(targetID, String.valueOf(event.getData().get(Integer.parseInt(String.valueOf(userTxt.charAt(1)))-1).getQuota()));
                break;
            } else if (userTxt.contains("registrants")){
                pushMessage(targetID, String.valueOf(event.getData().get(Integer.parseInt(String.valueOf(userTxt.charAt(1)))-1).getRegistrants()));
                break;
            } else if (userTxt.contains("address")){
                pushMessage(targetID, html2text(event.getData().get(Integer.parseInt(String.valueOf(userTxt.charAt(1)))-1).getAddress()).replaceAll("\\<.*?>",""));
                break;
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
        int maxLength = (name.length() < 60)?name.length():60;
        name = name.substring(0, maxLength);
        CarouselTemplate carouselTemplate = new CarouselTemplate(
                    Arrays.asList(new CarouselColumn
                                    (poster_url, owner, name, Arrays.asList
                                        (new MessageAction("Summary", "["+String.valueOf(position+1)+"]"+" Summary : " + name),
                                         new MessageAction("Description", "["+String.valueOf(position+1)+"]"+" Description : " + name ),
                                         new URIAction("Join Event", uri))),
                                    new CarouselColumn
                                    (poster_url, owner, name, Arrays.asList
                                            (new MessageAction("Quota", "["+String.valueOf(position+1)+"]"+" Quota : " + name),
                                                    new MessageAction("Registrants", "["+String.valueOf(position+1)+"]"+" Registrants : " + name ),
                                                    new MessageAction("Address", "["+String.valueOf(position+1)+"]"+" Address : " + name)))));

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

    private void processText(String replyToken, String userId, String text)
    {
        System.out.println("message text: " + text + " from: " + userId);

        if (text.indexOf("\"") == -1){
            replyToUser(replyToken, "Unknown keyword");
            return;
        }

        String [] words=text.trim().split("\\s+");
        String intent=words[0];
        System.out.println("intent: " + intent);
        String msg = " ";

        String lineId = " ";
        String displayName = " ";

        if(intent.equalsIgnoreCase("id"))
        {
            String target=words.length>1 ? words[1] : "";
            if (target.length()<=3)
            {
                msg = "Need more than 3 character to find person";
            }
            else
            {
                lineId = text.substring(text.indexOf("\"") + 1, text.lastIndexOf("\""));
//                System.out.println("Line ID: " + lineId);
//                displayName = aText.substring(aText.indexOf("#") + 1);
//                System.out.println("Display Name: " + displayName);
                String status = RegProcessor(userId, lineId, displayName);
                replyToUser(replyToken, status);
                return;
            }
        }
        else if(intent.equalsIgnoreCase("find"))
        {
            lineId = text.substring(text.indexOf("\"") + 1, text.lastIndexOf("\""));
            System.out.println("Line ID: " + lineId);
            String txtMessage = FindAll();
            replyToUser(replyToken, txtMessage);
            return;
        }

        // if msg is invalid
        if(msg == " ")
        {
            replyToUser(replyToken, "Unknown keyword");
        }
    }

    private String RegProcessor(String aUserId, String aLineId, String aDisplayName){
        String regStatus;
        String exist = FindProcessor(aLineId);
        if(exist=="User not found")
        {
            int reg=mDao.registerLineId(aUserId, aLineId, aDisplayName);
            if(reg==1)
            {
                regStatus="Successfully Registered";
            }
            else
            {
                regStatus="Registration process failed";
            }
        }
        else
        {
            regStatus="Already registered";
        }

        return regStatus;
    }

    private String FindProcessor(String aLineId){
        String txt="Find Result:";
        List<User> self=mDao.getByLineId("%"+aLineId+"%");
        if(self.size() > 0)
        {
            for (int i=0; i<self.size(); i++){
                User user=self.get(i);
                txt=txt+"\n\n";
                txt=txt+getPersonString(user);
            }

        }
        else
        {
            txt="Person not found";
        }
        return txt;
    }

    private String FindAll(){
        String txt="Find Result:";
        List<User> self=mDao.get();
        if(self.size() > 0)
        {
            for (int i=0; i<self.size(); i++){
                User user=self.get(i);
                txt=txt+"\n\n";
                txt=txt+getPersonString(user);
            }

        }
        else
        {
            txt="Person not found";
        }
        return txt;
    }

    private String getPersonString(User user)
    {
        return String.format("LINE ID: %s\n Display Name: %s\n", user.line_id, user.display_name);
    }
}
