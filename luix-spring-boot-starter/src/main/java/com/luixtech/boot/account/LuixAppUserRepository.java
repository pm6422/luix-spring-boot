package com.luixtech.boot.account;

import com.luixtech.core.id.TsidGenerator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Schema-portable {@link JdbcClient} repository for the unified {@code app_user} and {@code app_user_role} tables.
 *
 * <p>Uses standard ANSI SQL so it works identically on PostgreSQL and H2 without depending on application-specific
 * jOOQ generated classes.
 */
public class LuixAppUserRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, oidc_sub, email, display_name, picture_url, enabled,
                   time_zone_id, time_format_id, last_login_at, created_at, updated_at
            FROM app_user
            """;

    private final JdbcClient jdbcClient;

    public LuixAppUserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    public Optional<LuixAppUser> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Optional<LuixAppUser> userOpt = jdbcClient.sql(SELECT_COLUMNS + " WHERE id = :id")
                .param("id", id)
                .query(this::mapUser)
                .optional();
        userOpt.ifPresent(this::attachRoles);
        return userOpt;
    }

    public Optional<LuixAppUser> findByOidcSub(String oidcSub) {
        if (oidcSub == null || oidcSub.isBlank()) {
            return Optional.empty();
        }
        Optional<LuixAppUser> userOpt = jdbcClient.sql(SELECT_COLUMNS + " WHERE oidc_sub = :oidcSub")
                .param("oidcSub", oidcSub)
                .query(this::mapUser)
                .optional();
        userOpt.ifPresent(this::attachRoles);
        return userOpt;
    }

    public Optional<LuixAppUser> findByEmailIgnoreCase(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        Optional<LuixAppUser> userOpt = jdbcClient.sql(SELECT_COLUMNS + " WHERE LOWER(email) = :email")
                .param("email", email.trim().toLowerCase(Locale.ROOT))
                .query(this::mapUser)
                .optional();
        userOpt.ifPresent(this::attachRoles);
        return userOpt;
    }

    public List<LuixAppUser> findAllOrderByCreatedAtAsc() {
        List<LuixAppUser> users = jdbcClient.sql(SELECT_COLUMNS + " ORDER BY created_at ASC, id ASC")
                .query(this::mapUser)
                .list();
        if (users.isEmpty()) {
            return users;
        }

        Map<String, Set<String>> rolesByUserId = new LinkedHashMap<>();
        jdbcClient.sql("SELECT user_id, role FROM app_user_role ORDER BY role ASC")
                .query((rs, rowNum) -> {
                    rolesByUserId
                            .computeIfAbsent(rs.getString("user_id"), k -> new LinkedHashSet<>())
                            .add(rs.getString("role"));
                    return null;
                })
                .list();

        for (LuixAppUser user : users) {
            user.setRoles(rolesByUserId.getOrDefault(user.getId(), Set.of()));
        }
        return users;
    }

    public long count() {
        Long value = jdbcClient.sql("SELECT COUNT(*) FROM app_user")
                .query(Long.class)
                .single();
        return value != null ? value : 0L;
    }

    public long countEnabledByRole(String role) {
        if (role == null || role.isBlank()) {
            return 0L;
        }
        Long value = jdbcClient.sql("""
                        SELECT COUNT(DISTINCT u.id)
                        FROM app_user u
                        JOIN app_user_role r ON u.id = r.user_id
                        WHERE u.enabled = TRUE AND r.role = :role
                        """)
                .param("role", role)
                .query(Long.class)
                .single();
        return value != null ? value : 0L;
    }

    @Transactional
    public LuixAppUser save(LuixAppUser user) {
        Objects.requireNonNull(user, "user must not be null");
        Instant now = Instant.now();
        boolean isNew = user.getId() == null || user.getId().isBlank() || !existsById(user.getId());

        if (user.getId() == null || user.getId().isBlank()) {
            user.setId(TsidGenerator.nextIdString());
        }
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(now);
        }
        user.setUpdatedAt(now);

        if (isNew) {
            jdbcClient.sql("""
                            INSERT INTO app_user (
                                id, oidc_sub, email, display_name, picture_url, enabled,
                                time_zone_id, time_format_id, last_login_at, created_at, updated_at
                            ) VALUES (
                                :id, :oidcSub, :email, :displayName, :pictureUrl, :enabled,
                                :timeZoneId, :timeFormatId, :lastLoginAt, :createdAt, :updatedAt
                            )
                            """)
                    .param("id", user.getId())
                    .param("oidcSub", user.getOidcSub())
                    .param("email", user.getEmail())
                    .param("displayName", user.getDisplayName())
                    .param("pictureUrl", user.getPictureUrl())
                    .param("enabled", user.isEnabled())
                    .param("timeZoneId", user.getTimeZoneId())
                    .param("timeFormatId", user.getTimeFormatId())
                    .param("lastLoginAt", toTimestamp(user.getLastLoginAt()))
                    .param("createdAt", toTimestamp(user.getCreatedAt()))
                    .param("updatedAt", toTimestamp(user.getUpdatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            UPDATE app_user
                            SET oidc_sub = :oidcSub,
                                email = :email,
                                display_name = :displayName,
                                picture_url = :pictureUrl,
                                enabled = :enabled,
                                time_zone_id = :timeZoneId,
                                time_format_id = :timeFormatId,
                                last_login_at = :lastLoginAt,
                                updated_at = :updatedAt
                            WHERE id = :id
                            """)
                    .param("id", user.getId())
                    .param("oidcSub", user.getOidcSub())
                    .param("email", user.getEmail())
                    .param("displayName", user.getDisplayName())
                    .param("pictureUrl", user.getPictureUrl())
                    .param("enabled", user.isEnabled())
                    .param("timeZoneId", user.getTimeZoneId())
                    .param("timeFormatId", user.getTimeFormatId())
                    .param("lastLoginAt", toTimestamp(user.getLastLoginAt()))
                    .param("updatedAt", toTimestamp(user.getUpdatedAt()))
                    .update();
        }

        replaceRoles(user.getId(), user.getRoles());
        return user;
    }

    @Transactional
    public void deleteById(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        jdbcClient.sql("DELETE FROM app_user_role WHERE user_id = :userId")
                .param("userId", id)
                .update();
        jdbcClient.sql("DELETE FROM app_user WHERE id = :id")
                .param("id", id)
                .update();
    }

    private boolean existsById(String id) {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM app_user WHERE id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
        return count != null && count > 0L;
    }

    private void attachRoles(LuixAppUser user) {
        List<String> roles = jdbcClient.sql("SELECT role FROM app_user_role WHERE user_id = :userId ORDER BY role ASC")
                .param("userId", user.getId())
                .query(String.class)
                .list();
        user.setRoles(roles);
    }

    private void replaceRoles(String userId, Set<String> roles) {
        jdbcClient.sql("DELETE FROM app_user_role WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        if (roles == null || roles.isEmpty()) {
            return;
        }
        List<String> uniqueRoles = new ArrayList<>(new LinkedHashSet<>(roles));
        for (String role : uniqueRoles) {
            if (role != null && !role.isBlank()) {
                jdbcClient.sql("INSERT INTO app_user_role (user_id, role) VALUES (:userId, :role)")
                        .param("userId", userId)
                        .param("role", role.trim())
                        .update();
            }
        }
    }

    private LuixAppUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        LuixAppUser user = new LuixAppUser();
        user.setId(rs.getString("id"));
        user.setOidcSub(rs.getString("oidc_sub"));
        user.setEmail(rs.getString("email"));
        user.setDisplayName(rs.getString("display_name"));
        user.setPictureUrl(rs.getString("picture_url"));
        user.setEnabled(rs.getBoolean("enabled"));
        user.setTimeZoneId(rs.getString("time_zone_id"));
        user.setTimeFormatId(rs.getString("time_format_id"));
        user.setLastLoginAt(toInstant(rs.getTimestamp("last_login_at")));
        user.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        user.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        return user;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
