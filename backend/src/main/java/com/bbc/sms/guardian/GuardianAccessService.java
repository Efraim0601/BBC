package com.bbc.sms.guardian;

import com.bbc.sms.platform.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class GuardianAccessService {
    private final JdbcTemplate jdbc;
    public GuardianAccessService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<UUID> childIds(UUID schoolId,UUID userId){return jdbc.query("""
      SELECT sg.student_id FROM guardian g JOIN student_guardian sg ON sg.guardian_id=g.id
      WHERE g.school_id=? AND g.app_user_id=? AND g.status='ACTIVE' AND sg.effective_to IS NULL AND sg.portal_access=true
      ORDER BY sg.student_id
      """,(rs,i)->(UUID)rs.getObject(1),schoolId,userId);}
    public void assertAccess(UUID schoolId,UUID userId,UUID studentId,String feature){
        String column=switch(feature){case "finance"->"receives_finance";case "attendance"->"receives_attendance";case "discipline"->"receives_discipline";case "health"->"receives_health";default->"receives_academic";};
        Integer n=jdbc.queryForObject("SELECT count(*) FROM guardian g JOIN student_guardian sg ON sg.guardian_id=g.id WHERE g.school_id=? AND g.app_user_id=? AND sg.student_id=? AND g.status='ACTIVE' AND sg.effective_to IS NULL AND sg.portal_access=true AND sg."+column+"=true",Integer.class,schoolId,userId,studentId);
        if(n==null||n==0)throw new ApiException(HttpStatus.FORBIDDEN,"Cette relation familiale n’autorise pas l’accès à cette rubrique. Contactez l’établissement.");
    }
}
