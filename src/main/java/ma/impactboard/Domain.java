package ma.impactboard;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

enum Role { MANAGER, PARTNER, ADMIN }
enum ProjectStatus { ACTIVE, COMPLETED, PAUSED }
enum Frequency { WEEKLY, MONTHLY, QUARTERLY, YEARLY }

@Entity @Table(name="app_users")
class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false, unique=true) String username;
    @Column(nullable=false) String passwordHash;
    @Column(nullable=false) String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable=false) Role role;
    protected AppUser() {}
    AppUser(String username, String passwordHash, String displayName, Role role) { this.username=username; this.passwordHash=passwordHash; this.displayName=displayName; this.role=role; }
}

@Entity @Table(name="projects")
class Project {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false) String name;
    @Column(length=2000) String description;
    @Column(nullable=false) LocalDate startDate;
    @Column(nullable=false) LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false) ProjectStatus status;
    @ManyToOne(optional=false, fetch=FetchType.EAGER) AppUser owner;
    @ManyToMany(fetch=FetchType.EAGER) @JoinTable(name="project_readers", joinColumns=@JoinColumn(name="project_id"), inverseJoinColumns=@JoinColumn(name="user_id")) Set<AppUser> readers=new HashSet<>();
    protected Project() {}
}

@Entity @Table(name="kpis")
class Kpi {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(optional=false, fetch=FetchType.EAGER) Project project;
    @Column(nullable=false) String name;
    @Column(length=1200) String description;
    @Column(nullable=false) String unit;
    @Column(nullable=false) double target;
    @Enumerated(EnumType.STRING) @Column(nullable=false) Frequency frequency;
    @Column(nullable=false) boolean evidenceRequired;
    protected Kpi() {}
}

@Entity @Table(name="measurements")
class Measurement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(optional=false, fetch=FetchType.EAGER) Kpi kpi;
    @Column(nullable=false) LocalDate periodDate;
    @Column(nullable=false) double value;
    @Column(length=1500) String notes;
    @Column(nullable=false) Instant createdAt;
    @ManyToOne(optional=false, fetch=FetchType.EAGER) AppUser author;
    @OneToMany(mappedBy="measurement", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.EAGER) List<Evidence> evidence=new ArrayList<>();
    protected Measurement() {}
}

@Entity @Table(name="evidence")
class Evidence {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) Measurement measurement;
    @Column(nullable=false) String originalName;
    @Column(nullable=false, unique=true) String storedName;
    @Column(nullable=false) String contentType;
    @Column(nullable=false) long size;
    protected Evidence() {}
}

@Entity @Table(name="measurement_audit")
class MeasurementAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false) Long measurementId;
    @Column(nullable=false) double value;
    @Column(nullable=false) LocalDate periodDate;
    @Column(nullable=false) Instant changedAt;
    @Column(nullable=false) Long authorId;
    @Column(nullable=false) String action;
    protected MeasurementAudit() {}
    MeasurementAudit(Measurement m, String action) { measurementId=m.id; value=m.value; periodDate=m.periodDate; changedAt=Instant.now(); authorId=m.author.id; this.action=action; }
}
