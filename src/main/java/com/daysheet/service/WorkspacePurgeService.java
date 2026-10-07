package com.daysheet.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Removes signups that never verified their email, so the database only holds genuine accounts.
 * Only practices whose every user is unverified and that have no clients are ever touched.
 */
@Service
@RequiredArgsConstructor
public class WorkspacePurgeService {

    private static final Logger log = LoggerFactory.getLogger(WorkspacePurgeService.class);

    private final EntityManager em;

    @Value("${app.email.unverified-retention-days:3}")
    private int retentionDays;

    /** Deletes one unverified practice and the little it contains. Returns false if it isn't safe to delete. */
    @Transactional
    public boolean purgeUnverified(Long workspaceId) {
        Long verifiedUsers = em.createQuery("""
                select count(u) from AppUser u where u.workspace.id = :ws and (u.emailVerified is null or u.emailVerified = true)
                """, Long.class).setParameter("ws", workspaceId).getSingleResult();
        Long clients = em.createQuery("select count(c) from Client c where c.workspace.id = :ws", Long.class)
                .setParameter("ws", workspaceId).getSingleResult();
        if (verifiedUsers > 0 || clients > 0) return false;

        String users = "(select u.id from AppUser u where u.workspace.id = :ws)";
        em.createQuery("delete from EmailVerification v where v.user.id in " + users).setParameter("ws", workspaceId).executeUpdate();
        em.createQuery("delete from PasswordResetToken t where t.user.id in " + users).setParameter("ws", workspaceId).executeUpdate();
        em.createQuery("delete from AppUser u where u.workspace.id = :ws").setParameter("ws", workspaceId).executeUpdate();
        em.createQuery("delete from WorkingHours h where h.workspace.id = :ws").setParameter("ws", workspaceId).executeUpdate();
        em.createQuery("delete from PackageTemplate p where p.workspace.id = :ws").setParameter("ws", workspaceId).executeUpdate();
        em.createQuery("delete from ServiceOffering s where s.workspace.id = :ws").setParameter("ws", workspaceId).executeUpdate();
        em.createQuery("delete from Workspace w where w.id = :ws").setParameter("ws", workspaceId).executeUpdate();
        em.clear();
        return true;
    }

    /** Daily at 3:30 am IST. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void purgeStale() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        List<Long> ids = em.createQuery("""
                select distinct u.workspace.id from AppUser u where u.emailVerified = false and u.createdAt < :cutoff
                """, Long.class).setParameter("cutoff", cutoff).getResultList();
        int removed = 0;
        for (Long id : ids) if (purgeUnverified(id)) removed++;
        if (removed > 0) log.info("Removed {} signup(s) that never verified their email.", removed);
    }

}
