package com.wujia.pet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wujia.pet.entity.UserAccount;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Service
public class MiniAppRealtimeService extends TextWebSocketHandler {
    private final MiniAppTokenService tokens;
    private final ObjectMapper objectMapper;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    public MiniAppRealtimeService(MiniAppTokenService tokens, ObjectMapper objectMapper) { this.tokens=tokens;this.objectMapper=objectMapper; }

    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token=query(session,"token");
        UserAccount user=tokens.optionalUser(token).orElse(null);
        clients.put(session.getId(),new Client(session,user==null?null:user.getId(),ConcurrentHashMap.newKeySet(),ConcurrentHashMap.newKeySet()));
        send(session,Map.of("type","connected","authenticated",user!=null));
    }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Client client=clients.get(session.getId()); if(client==null)return;
        Map<?,?> data=objectMapper.readValue(message.getPayload(),Map.class);String type=String.valueOf(data.get("type"));
        if("ping".equals(type)){send(session,Map.of("type","pong"));return;}
        if("watchGroup".equals(type)){client.groups().add(Long.parseLong(String.valueOf(data.get("groupId"))));return;}
        if("watchFace".equals(type)){client.faceCodes().add(String.valueOf(data.get("code")));}
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status){clients.remove(session.getId());}
    @Override public void handleTransportError(WebSocketSession session, Throwable exception){clients.remove(session.getId());try{session.close();}catch(Exception ignored){}}

    public void group(Long groupId,String type,Object payload){broadcast(client->client.groups().contains(groupId),type,payload);}
    public void face(String code,String type,Object payload){broadcast(client->client.faceCodes().contains(code),type,payload);}
    public void user(Long userId,String type,Object payload){broadcast(client->Objects.equals(client.userId(),userId),type,payload);}
    private void broadcast(java.util.function.Predicate<Client> predicate,String type,Object payload){Map<String,Object> value=new LinkedHashMap<>();value.put("type",type);value.put("data",payload);clients.values().stream().filter(predicate).forEach(client->{try{send(client.session(),value);}catch(Exception ignored){}});}
    private void send(WebSocketSession session,Object value)throws Exception{if(session.isOpen())session.sendMessage(new TextMessage(objectMapper.writeValueAsString(value)));}
    private String query(WebSocketSession session,String name){String query=session.getUri()==null?null:session.getUri().getRawQuery();if(query==null)return"";for(String item:query.split("&")){String[] pair=item.split("=",2);if(pair.length==2&&name.equals(pair[0]))return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);}return"";}
    private record Client(WebSocketSession session,Long userId,Set<Long> groups,Set<String> faceCodes){}
}
