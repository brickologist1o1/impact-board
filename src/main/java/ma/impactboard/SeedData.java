package ma.impactboard;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;

@Configuration
class SeedData {
    @Bean CommandLineRunner seed(UserRepository users, ProjectRepository projects, KpiRepository kpis, MeasurementRepository measurements, PasswordEncoder encoder) {
        return args -> {
            if (users.count()>0) return;
            AppUser manager=users.save(new AppUser("manager",encoder.encode("impact2026"),"Sara El Amrani",Role.MANAGER));
            AppUser partner=users.save(new AppUser("partenaire",encoder.encode("impact2026"),"Partenaire Démo",Role.PARTNER));
            users.save(new AppUser("admin",encoder.encode("impact2026"),"Administrateur",Role.ADMIN));
            Project p=new Project(); p.name="École pour toutes"; p.description="Accompagner les jeunes filles vers la réussite scolaire grâce au mentorat et à l'accès aux ressources éducatives."; p.startDate=LocalDate.now().minusMonths(5); p.endDate=LocalDate.now().plusMonths(7); p.status=ProjectStatus.ACTIVE; p.owner=manager; p.readers.add(partner); p=projects.save(p);
            Kpi k=new Kpi(); k.project=p; k.name="Élèves accompagnées"; k.description="Nombre d'élèves ayant suivi au moins une séance de mentorat."; k.unit="élèves"; k.target=120; k.frequency=Frequency.MONTHLY; k.evidenceRequired=false; k=kpis.save(k);
            addMeasurement(measurements,k,LocalDate.now().minusMonths(3),32,manager); addMeasurement(measurements,k,LocalDate.now().minusMonths(2),58,manager); addMeasurement(measurements,k,LocalDate.now().minusMonths(1),86,manager);
            Kpi k2=new Kpi(); k2.project=p; k2.name="Kits scolaires distribués"; k2.description="Kits complets remis aux élèves inscrites."; k2.unit="kits"; k2.target=100; k2.frequency=Frequency.MONTHLY; k2.evidenceRequired=false; k2=kpis.save(k2);
            addMeasurement(measurements,k2,LocalDate.now().minusMonths(2),25,manager); addMeasurement(measurements,k2,LocalDate.now().minusMonths(1),54,manager);
        };
    }
    private static void addMeasurement(MeasurementRepository repo,Kpi k,LocalDate date,double value,AppUser author){ Measurement m=new Measurement(); m.kpi=k; m.periodDate=date; m.value=value; m.createdAt=java.time.Instant.now(); m.author=author; repo.save(m); }
}
