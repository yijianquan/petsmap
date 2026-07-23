package com.wujia.pet.controller;

import com.wujia.pet.entity.*;
import com.wujia.pet.repository.*;
import com.wujia.pet.service.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/miniapp/api")
public class FaceJoinController {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final FaceJoinService faces; private final MiniAppTokenService tokens; private final MiniAppRealtimeService realtime;
    private final WalkGroupRepository groups; private final WalkGroupMemberRepository members; private final WalkGroupJoinRequestRepository requests; private final UserAccountRepository users;
    public FaceJoinController(FaceJoinService faces,MiniAppTokenService tokens,MiniAppRealtimeService realtime,WalkGroupRepository groups,WalkGroupMemberRepository members,WalkGroupJoinRequestRepository requests,UserAccountRepository users){this.faces=faces;this.tokens=tokens;this.realtime=realtime;this.groups=groups;this.members=members;this.requests=requests;this.users=users;}

    @PostMapping("/walk-groups/{groupId}/face-sessions")
    public Map<String,Object> create(@RequestHeader(value="X-Miniapp-Token",required=false)String token,@PathVariable Long groupId,@RequestBody LocationRequest body){
        UserAccount user=tokens.requireUser(token);WalkGroup group=requireGroup(groupId);WalkGroupMember member=requireMember(groupId,user.getId());
        boolean direct=isManager(group,member,user);FaceJoinService.Session session=faces.create(groupId,user.getId(),body.latitude(),body.longitude(),direct);
        return sessionDto(session,group);
    }

    @PostMapping("/face-sessions/enter") @Transactional
    public Map<String,Object> enter(@RequestHeader(value="X-Miniapp-Token",required=false)String token,@RequestBody EnterRequest body){
        UserAccount user=tokens.requireUser(token);FaceJoinService.Session session=faces.enter(body.code(),user.getId(),body.latitude(),body.longitude());WalkGroup group=requireGroup(session.groupId());
        String status;
        if(members.existsByGroupIdAndUserId(group.getId(),user.getId())) status="JOINED";
        else if(session.direct()){addMember(group,user,"MEMBER");status="JOINED";realtime.group(group.getId(),"group.memberJoined",Map.of("groupId",group.getId(),"userId",user.getId()));}
        else {WalkGroupJoinRequest request=requests.findFirstByGroupIdAndApplicantIdAndStatusOrderByCreatedAtDesc(group.getId(),user.getId(),"PENDING").orElseGet(WalkGroupJoinRequest::new);request.setGroup(group);request.setApplicant(user);request.setInviter(users.findById(session.inviterId()).orElseThrow());request.setStatus("PENDING");request.setCreatedAt(LocalDateTime.now());requests.save(request);status="PENDING";notifyManagers(group,"join.request",requestDto(request));}
        Map<String,Object> result=new LinkedHashMap<>(sessionDto(session,group));result.put("status",status);realtime.face(session.code(),"face.updated",Map.of("code",session.code(),"status",status));return result;
    }

    @GetMapping("/face-sessions/{code}")
    public Map<String,Object> status(@RequestHeader(value="X-Miniapp-Token",required=false)String token,@PathVariable String code){UserAccount viewer=tokens.requireUser(token);FaceJoinService.Session session=faces.require(code);WalkGroup group=requireGroup(session.groupId());Map<String,Object> result=new LinkedHashMap<>(sessionDto(session,group));result.put("participants",users.findAllById(faces.participants(code)).stream().filter(user->!Objects.equals(user.getId(),viewer.getId())).map(this::userDto).toList());return result;}

    @GetMapping("/walk-groups/{groupId}/join-requests")
    public List<Map<String,Object>> pending(@RequestHeader(value="X-Miniapp-Token",required=false)String token,@PathVariable Long groupId){UserAccount user=tokens.requireUser(token);WalkGroup group=requireGroup(groupId);requireManager(group,user);return requests.findByGroupIdAndStatusOrderByCreatedAtAsc(groupId,"PENDING").stream().map(this::requestDto).toList();}

    @PostMapping("/walk-groups/{groupId}/join-requests/{requestId}/{action}") @Transactional
    public Map<String,Object> handle(@RequestHeader(value="X-Miniapp-Token",required=false)String token,@PathVariable Long groupId,@PathVariable Long requestId,@PathVariable String action){UserAccount manager=tokens.requireUser(token);WalkGroup group=requireGroup(groupId);requireManager(group,manager);WalkGroupJoinRequest request=requests.findById(requestId).orElseThrow(()->new IllegalArgumentException("申请不存在。"));if(!Objects.equals(request.getGroup().getId(),groupId)||!"PENDING".equals(request.getStatus()))throw new IllegalArgumentException("申请已处理。");boolean approved="approve".equals(action);if(!approved&&!"reject".equals(action))throw new IllegalArgumentException("不支持的操作。");request.setStatus(approved?"APPROVED":"REJECTED");request.setHandledAt(LocalDateTime.now());requests.save(request);if(approved&&!members.existsByGroupIdAndUserId(groupId,request.getApplicant().getId()))addMember(group,request.getApplicant(),"MEMBER");Map<String,Object> dto=requestDto(request);realtime.user(request.getApplicant().getId(),approved?"join.approved":"join.rejected",dto);realtime.group(groupId,"group.joinRequestHandled",dto);return dto;}

    @PutMapping("/walk-groups/{groupId}/members/{userId}/role")
    public Map<String,Object> role(@RequestHeader(value="X-Miniapp-Token",required=false)String token,@PathVariable Long groupId,@PathVariable Long userId,@RequestBody RoleRequest body){UserAccount owner=tokens.requireUser(token);WalkGroup group=requireGroup(groupId);if(!Objects.equals(group.getOwner().getId(),owner.getId()))throw new IllegalArgumentException("只有群主可以设置管理员。");WalkGroupMember member=requireMember(groupId,userId);String role="ADMIN".equalsIgnoreCase(body.role())?"ADMIN":"MEMBER";member.setRole(role);members.save(member);return Map.of("userId",userId,"role",role);}

    private WalkGroup requireGroup(Long id){return groups.findById(id).orElseThrow(()->new IllegalArgumentException("群聊不存在或已解散。"));}
    private WalkGroupMember requireMember(Long groupId,Long userId){return members.findByGroupIdAndUserId(groupId,userId).orElseThrow(()->new IllegalArgumentException("请先加入群聊。"));}
    private boolean isManager(WalkGroup group,WalkGroupMember member,UserAccount user){return Objects.equals(group.getOwner().getId(),user.getId())||"ADMIN".equals(member.getRole());}
    private void requireManager(WalkGroup group,UserAccount user){WalkGroupMember member=requireMember(group.getId(),user.getId());if(!isManager(group,member,user))throw new IllegalArgumentException("只有群主或管理员可以审批。");}
    private void addMember(WalkGroup group,UserAccount user,String role){WalkGroupMember member=new WalkGroupMember();member.setGroup(group);member.setUser(user);member.setRole(role);member.setJoinedAt(LocalDateTime.now());members.save(member);}
    private void notifyManagers(WalkGroup group,String type,Object payload){realtime.user(group.getOwner().getId(),type,payload);members.findByGroupIdOrderByJoinedAtAsc(group.getId()).stream().filter(m->"ADMIN".equals(m.getRole())).forEach(m->realtime.user(m.getUser().getId(),type,payload));}
    private Map<String,Object> sessionDto(FaceJoinService.Session s,WalkGroup g){Map<String,Object>d=new LinkedHashMap<>();d.put("code",s.code());d.put("groupId",g.getId());d.put("groupName",g.getName());d.put("placeName",g.getPlace().getName());d.put("direct",s.direct());d.put("expiresAt",s.expiresAt());return d;}
    private Map<String,Object> userDto(UserAccount u){return Map.of("id",u.getId(),"nickname",displayName(u),"avatarUrl",avatar(u));}
    private Map<String,Object> requestDto(WalkGroupJoinRequest r){Map<String,Object>d=new LinkedHashMap<>();d.put("id",r.getId());d.put("groupId",r.getGroup().getId());d.put("applicantId",r.getApplicant().getId());d.put("applicantName",displayName(r.getApplicant()));d.put("avatarUrl",avatar(r.getApplicant()));d.put("inviterName",displayName(r.getInviter()));d.put("status",r.getStatus());d.put("createdAt",r.getCreatedAt().format(TIME));return d;}
    private String displayName(UserAccount u){return u.getNickname()==null||u.getNickname().isBlank()?u.getUsername():u.getNickname();}
    private String avatar(UserAccount u){return u.hasAvatarData()?"/miniapp/api/users/"+u.getId()+"/avatar":u.getAvatarUrl()==null?"":u.getAvatarUrl();}
    private record LocationRequest(double latitude,double longitude){} private record EnterRequest(String code,double latitude,double longitude){} private record RoleRequest(String role){}
}
