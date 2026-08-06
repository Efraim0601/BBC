package com.bbc.sms.guardian;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.identity.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.OffsetDateTime;
import java.util.*;
import static com.bbc.sms.guardian.GuardianDtos.*;

@Service
public class GuardianAccountService {
    private final JdbcTemplate jdbc; private final AppUserRepository users; private final SchoolRepository schools;
    private final PasswordEncoder encoder; private final MailService mail; private final AuditService audit;
    public GuardianAccountService(JdbcTemplate jdbc,AppUserRepository users,SchoolRepository schools,PasswordEncoder encoder,MailService mail,AuditService audit){this.jdbc=jdbc;this.users=users;this.schools=schools;this.encoder=encoder;this.mail=mail;this.audit=audit;}

    @Transactional
    public InviteResult issueInvite(UUID guardianId){
        var g=guardian(guardianId); if(g.email()==null)throw ApiException.badRequest("Ce parent n’a pas d’adresse e-mail");
        Integer recent=jdbc.queryForObject("SELECT count(*) FROM guardian_account_token WHERE guardian_id=? AND token_type='INVITE' AND created_at>now()-interval '60 seconds'",Integer.class,guardianId);
        if(recent!=null&&recent>0)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Une invitation vient déjà d’être envoyée. Réessayez dans une minute.");
        Token issued=token(guardianId,"INVITE",48);
        jdbc.update("UPDATE guardian SET status='INVITED',updated_at=now() WHERE id=?",guardianId);
        mail.sendParentAction(g.schoolId(),g.name(),g.email(),"Invitation au portail parent",issued.raw(),"invitation");
        audit.record("GUARDIAN_INVITE_SENT","Guardian",guardianId.toString(),null,Map.of("destination",mask(g.email())),"Invitation parent");
        return new InviteResult(guardianId,"SENT",mask(g.email()),issued.expires().toString());
    }

    @Transactional
    public PublicMessage accept(AcceptInviteRequest req){
        TokenRow t=consume(req.token(),"INVITE"); validatePassword(req.password()); GuardianRow g=guardianAnySchool(t.guardianId());
        AppUser u=users.findById(g.userId()).orElseThrow(()->ApiException.notFound("Compte parent"));
        u.setPasswordHash(encoder.encode(req.password()));u.setMustChangePassword(false);u.setActive(true);u.setEmailVerifiedAt(OffsetDateTime.now());u.setFailedAttempts(0);u.setLockedUntil(null);u.setCredentialsVersion(u.getCredentialsVersion()+1);users.save(u);
        jdbc.update("UPDATE guardian SET status='ACTIVE',updated_at=now() WHERE id=?",g.id());
        audit.record("GUARDIAN_INVITE_ACCEPTED","Guardian",g.id().toString(),null,Map.of("active",true),"Invitation acceptée");
        return new PublicMessage("Compte parent activé. Vous pouvez maintenant vous connecter.");
    }

    @Transactional
    public PublicMessage forgot(ForgotParentPasswordRequest req){
        schools.findByCode(req.schoolCode()).ifPresent(s->{
            users.findBySchoolIdAndNormalizedEmail(s.getId(),GuardianService.normalEmail(req.email())).filter(u->"parent".equals(u.getRoleCode())&&u.isActive()).ifPresent(u->{
                List<UUID> ids=jdbc.query("SELECT id FROM guardian WHERE school_id=? AND app_user_id=? AND status<>'MERGED'",(rs,i)->(UUID)rs.getObject(1),s.getId(),u.getId());
                if(!ids.isEmpty()){Token t=tokenForSchool(s.getId(),ids.getFirst(),"RESET_PASSWORD",2);mail.sendParentAction(s.getId(),u.getDisplayName(),u.getEmail(),"Réinitialisation du mot de passe parent",t.raw(),"réinitialisation");}
            });
        });
        return new PublicMessage("Si ce compte existe, un message de réinitialisation a été envoyé.");
    }

    @Transactional
    public PublicMessage reset(ResetParentPasswordRequest req){
        TokenRow t=consume(req.token(),"RESET_PASSWORD");validatePassword(req.password());GuardianRow g=guardianAnySchool(t.guardianId());
        AppUser u=users.findById(g.userId()).orElseThrow(()->ApiException.notFound("Compte parent"));u.setPasswordHash(encoder.encode(req.password()));u.setFailedAttempts(0);u.setLockedUntil(null);u.setMustChangePassword(false);u.setCredentialsVersion(u.getCredentialsVersion()+1);users.save(u);
        jdbc.update("UPDATE guardian_account_token SET used_at=now() WHERE guardian_id=? AND used_at IS NULL",g.id());
        return new PublicMessage("Mot de passe modifié. Vous pouvez vous connecter.");
    }

    @Transactional public void deactivateIfOrphan(UUID guardianId){Integer n=jdbc.queryForObject("SELECT count(*) FROM student_guardian WHERE guardian_id=? AND effective_to IS NULL",Integer.class,guardianId);if(n!=null&&n==0){GuardianRow g=guardian(guardianId);jdbc.update("UPDATE guardian SET status='INACTIVE' WHERE id=?",guardianId);if(g.userId()!=null)users.findById(g.userId()).ifPresent(u->{u.setActive(false);users.save(u);});}}
    @Transactional public void reactivate(UUID guardianId,String reason){GuardianRow g=guardian(guardianId);jdbc.update("UPDATE guardian SET status='ACTIVE' WHERE id=?",guardianId);if(g.userId()!=null)users.findById(g.userId()).ifPresent(u->{u.setActive(true);u.setFailedAttempts(0);u.setLockedUntil(null);users.save(u);});audit.record("GUARDIAN_REACTIVATED","Guardian",guardianId.toString(),null,Map.of("active",true),reason);}
    @Transactional public void deactivate(UUID guardianId,String reason){Integer n=jdbc.queryForObject("SELECT count(*) FROM student_guardian WHERE guardian_id=? AND effective_to IS NULL",Integer.class,guardianId);if(n!=null&&n>0)throw ApiException.conflict("Terminez d’abord les relations actives de ce parent");deactivateIfOrphan(guardianId);audit.record("GUARDIAN_DEACTIVATED","Guardian",guardianId.toString(),null,Map.of("active",false),reason);}

    private Token token(UUID gid,String type,int hours){return tokenForSchool(TenantContext.get(),gid,type,hours);}
    private Token tokenForSchool(UUID school,UUID gid,String type,int hours){String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(random(32));OffsetDateTime exp=OffsetDateTime.now().plusHours(hours);jdbc.update("UPDATE guardian_account_token SET used_at=now() WHERE guardian_id=? AND token_type=? AND used_at IS NULL",gid,type);jdbc.update("INSERT INTO guardian_account_token(id,school_id,guardian_id,token_type,token_hash,expires_at) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),school,gid,type,hash(raw),exp);return new Token(raw,exp);}
    private TokenRow consume(String raw,String type){List<TokenRow> r=jdbc.query("SELECT guardian_id,school_id FROM guardian_account_token WHERE token_hash=? AND token_type=? AND used_at IS NULL AND expires_at>now()",(rs,i)->new TokenRow((UUID)rs.getObject(1),(UUID)rs.getObject(2)),hash(raw),type);if(r.isEmpty())throw ApiException.badRequest("Lien invalide, expiré ou déjà utilisé");jdbc.update("UPDATE guardian_account_token SET used_at=now() WHERE token_hash=?",hash(raw));return r.getFirst();}
    private GuardianRow guardian(UUID id){List<GuardianRow> r=jdbc.query("SELECT id,school_id,app_user_id,display_name,email FROM guardian WHERE id=? AND school_id=? AND status<>'MERGED'",(rs,i)->new GuardianRow((UUID)rs.getObject(1),(UUID)rs.getObject(2),(UUID)rs.getObject(3),rs.getString(4),rs.getString(5)),id,TenantContext.get());if(r.isEmpty())throw ApiException.notFound("Parent");return r.getFirst();}
    private GuardianRow guardianAnySchool(UUID id){return jdbc.queryForObject("SELECT id,school_id,app_user_id,display_name,email FROM guardian WHERE id=?",(rs,i)->new GuardianRow((UUID)rs.getObject(1),(UUID)rs.getObject(2),(UUID)rs.getObject(3),rs.getString(4),rs.getString(5)),id);}
    private static byte[] random(int n){byte[] b=new byte[n];new SecureRandom().nextBytes(b);return b;}
    private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void validatePassword(String p){if(p==null||p.length()<8||!p.matches(".*[A-Za-z].*")||!p.matches(".*\\d.*"))throw ApiException.badRequest("Le mot de passe doit contenir au moins 8 caractères, une lettre et un chiffre");}
    private static String mask(String e){int at=e.indexOf('@');return at<2?"***":e.substring(0,1)+"***"+e.substring(at);}
    private record Token(String raw,OffsetDateTime expires){} private record TokenRow(UUID guardianId,UUID schoolId){} private record GuardianRow(UUID id,UUID schoolId,UUID userId,String name,String email){}
}
