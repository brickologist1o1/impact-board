package ma.impactboard;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface UserRepository extends JpaRepository<AppUser,Long> { Optional<AppUser> findByUsername(String username); }
interface ProjectRepository extends JpaRepository<Project,Long> {
    List<Project> findDistinctByOwner_IdOrReaders_IdOrderByIdDesc(Long ownerId, Long readerId);
    Optional<Project> findByIdAndOwner_Id(Long id, Long ownerId);
}
interface KpiRepository extends JpaRepository<Kpi,Long> { List<Kpi> findByProject_IdOrderById(Long projectId); Optional<Kpi> findByIdAndProject_Id(Long id, Long projectId); }
interface MeasurementRepository extends JpaRepository<Measurement,Long> { List<Measurement> findByKpi_IdOrderByPeriodDate(Long kpiId); Optional<Measurement> findByIdAndKpi_Id(Long id, Long kpiId); }
interface AuditRepository extends JpaRepository<MeasurementAudit,Long> { List<MeasurementAudit> findByMeasurementIdOrderByChangedAtDesc(Long measurementId); }
interface EvidenceRepository extends JpaRepository<Evidence,Long> { Optional<Evidence> findByIdAndMeasurement_Id(Long id, Long measurementId); }
