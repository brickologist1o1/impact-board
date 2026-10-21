package ma.impactboard;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
class Api {
    private final UserRepository users; private final ProjectRepository projects; private final KpiRepository kpis;
    private final MeasurementRepository measurements; private final EvidenceRepository evidences; private final AuditRepository audits;
    private final AuthenticationManager authManager; private final SecurityContextRepository contextRepo=new HttpSessionSecurityContextRepository();
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final Path uploadPath;
    Api(UserRepository users, ProjectRepository projects, KpiRepository kpis, MeasurementRepository measurements, EvidenceRepository evidences, AuditRepository audits, AuthenticationManager authManager, org.springframework.security.crypto.password.PasswordEncoder passwordEncoder, @Value("${app.upload-dir}") String dir) throws IOException {
        this.users=users; this.projects=projects; this.kpis=kpis; this.measurements=measurements; this.evidences=evidences; this.audits=audits; this.authManager=authManager; this.passwordEncoder=passwordEncoder; this.uploadPath=Paths.get(dir).toAbsolutePath().normalize(); Files.createDirectories(uploadPath);
    }

    record Login(String username,String password) {}
    record UserInput(String username,String password,String displayName,Role role) {}
    record UserView(Long id,String username,String displayName,String role) {}
    record ProjectInput(String name,String description,LocalDate startDate,LocalDate endDate,ProjectStatus status,List<String> readers) {}
    record KpiInput(String name,String description,String unit,double target,Frequency frequency,boolean evidenceRequired) {}
    record MeasureInput(LocalDate periodDate,double value,String notes) {}

    @PostMapping("/auth/login") UserView login(@RequestBody Login input,HttpServletRequest request,HttpServletResponse response) {
        Authentication authentication; try { authentication=authManager.authenticate(new UsernamePasswordAuthenticationToken(input.username(),input.password())); }
        catch(AuthenticationException ex){ throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Identifiants incorrects."); }
        SecurityContext context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication); SecurityContextHolder.setContext(context); contextRepo.saveContext(context,request,response);
        return userView(user(authentication));
    }
    @GetMapping("/auth/me") UserView me(Authentication auth){ return userView(user(auth)); }
    @GetMapping("/admin/users") List<UserView> listUsers(Authentication auth){ requireAdmin(user(auth)); return users.findAll().stream().map(this::userView).toList(); }
    @PostMapping("/admin/users") @ResponseStatus(HttpStatus.CREATED) UserView createUser(@RequestBody UserInput input,Authentication auth){
        requireAdmin(user(auth)); if(input.username()==null||input.username().isBlank()||input.displayName()==null||input.displayName().isBlank()||input.password()==null||input.password().length()<8||input.role()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nom d’utilisateur, nom, rôle et mot de passe (8 caractères minimum) requis.");
        if(users.findByUsername(input.username().trim()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Ce nom d’utilisateur existe déjà.");
        return userView(users.save(new AppUser(input.username().trim(),passwordEncoder.encode(input.password()),input.displayName().trim(),input.role())));
    }
    @GetMapping("/projects") List<Map<String,Object>> listProjects(Authentication auth){
        AppUser u=user(auth); List<Project> rows=u.role==Role.ADMIN?projects.findAll():projects.findDistinctByOwner_IdOrReaders_IdOrderByIdDesc(u.id,u.id);
        return rows.stream().map(this::projectView).toList();
    }
    @PostMapping("/projects") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createProject(@RequestBody ProjectInput in,Authentication auth){
        requireManager(user(auth)); validateProject(in);
        Project p=new Project(); p.name=in.name().trim(); p.description=in.description(); p.startDate=in.startDate(); p.endDate=in.endDate(); p.status=in.status()==null?ProjectStatus.ACTIVE:in.status(); p.owner=user(auth); setReaders(p,in.readers()); return projectView(projects.save(p));
    }
    @PutMapping("/projects/{id}") Map<String,Object> updateProject(@PathVariable Long id,@RequestBody ProjectInput in,Authentication auth){
        AppUser actor=user(auth); requireManager(actor); validateProject(in); Project p=ownedProject(id,actor);
        p.name=in.name().trim(); p.description=in.description(); p.startDate=in.startDate(); p.endDate=in.endDate(); p.status=in.status()==null?p.status:in.status(); setReaders(p,in.readers()); return projectView(projects.save(p));
    }
    @DeleteMapping("/projects/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteProject(@PathVariable Long id,Authentication auth){ requireManager(user(auth)); projects.delete(ownedProject(id,user(auth))); }
    @GetMapping("/projects/{id}") Map<String,Object> getProject(@PathVariable Long id,Authentication auth){ return projectView(visibleProject(id,user(auth))); }
    @PostMapping("/projects/{id}/kpis") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createKpi(@PathVariable Long id,@RequestBody KpiInput in,Authentication auth){
        AppUser actor=user(auth); requireManager(actor); Project p=ownedProject(id,actor); validateKpi(in); Kpi k=new Kpi(); applyKpi(k,in); k.project=p; return kpiView(kpis.save(k));
    }
    @PutMapping("/projects/{id}/kpis/{kid}") Map<String,Object> updateKpi(@PathVariable Long id,@PathVariable Long kid,@RequestBody KpiInput in,Authentication auth){
        AppUser actor=user(auth); requireManager(actor); ownedProject(id,actor); validateKpi(in); Kpi k=kpi(id,kid); applyKpi(k,in); return kpiView(kpis.save(k));
    }
    @DeleteMapping("/projects/{id}/kpis/{kid}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteKpi(@PathVariable Long id,@PathVariable Long kid,Authentication auth){ AppUser actor=user(auth); requireManager(actor); ownedProject(id,actor); kpis.delete(kpi(id,kid)); }
    @GetMapping("/projects/{id}/dashboard") @Transactional(readOnly=true) Map<String,Object> dashboard(@PathVariable Long id,Authentication auth){
        Project p=visibleProject(id,user(auth)); List<Map<String,Object>> cards=new ArrayList<>();
        for(Kpi k:kpis.findByProject_IdOrderById(p.id)){ List<Measurement> ms=measurements.findByKpi_IdOrderByPeriodDate(k.id); double total=ms.stream().mapToDouble(m->m.value).sum();
            List<Map<String,Object>> series=ms.stream().map(m->Map.<String,Object>of("date",m.periodDate,"value",m.value,"id",m.id,"evidenceCount",m.evidence.size(),"notes",Objects.toString(m.notes,""),"evidence",m.evidence.stream().map(e->Map.<String,Object>of("id",e.id,"name",e.originalName)).toList())).toList();
            cards.add(Map.of("id",k.id,"name",k.name,"description",Objects.toString(k.description,""),"unit",k.unit,"target",k.target,"frequency",k.frequency,"evidenceRequired",k.evidenceRequired,"total",total,"progress",k.target<=0?0:Math.min(100,total/k.target*100),"measurements",series));
        }
        return Map.of("project",projectView(p),"kpis",cards);
    }
    @PostMapping(value="/projects/{id}/kpis/{kid}/measurements",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    @Transactional Map<String,Object> createMeasurement(@PathVariable Long id,@PathVariable Long kid,@RequestPart("measurement") MeasureInput in,@RequestPart(value="files",required=false) List<MultipartFile> files,Authentication auth) throws IOException {
        AppUser actor=user(auth); requireManager(actor); ownedProject(id,actor); Kpi k=kpi(id,kid); validateMeasure(in);
        List<MultipartFile> validFiles=files==null?List.of():files.stream().filter(f->!f.isEmpty()).toList();
        if(k.evidenceRequired&&validFiles.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Une preuve est obligatoire pour ce KPI.");
        validateFiles(validFiles);
        Measurement m=new Measurement(); m.kpi=k; m.periodDate=in.periodDate(); m.value=in.value(); m.notes=in.notes(); m.createdAt=Instant.now(); m.author=actor; m=measurements.save(m);
        for(MultipartFile f:validFiles){ Evidence e=new Evidence(); e.measurement=m; e.originalName=Path.of(Objects.requireNonNull(f.getOriginalFilename())).getFileName().toString(); e.storedName=UUID.randomUUID().toString(); e.contentType=contentType(f); e.size=f.getSize(); f.transferTo(uploadPath.resolve(e.storedName)); m.evidence.add(e); }
        m=measurements.save(m); audits.save(new MeasurementAudit(m,"CREATED")); return measurementView(m);
    }
    @PutMapping(value="/projects/{id}/kpis/{kid}/measurements/{mid}",consumes=MediaType.APPLICATION_JSON_VALUE)
    @Transactional Map<String,Object> updateMeasurement(@PathVariable Long id,@PathVariable Long kid,@PathVariable Long mid,@RequestBody MeasureInput in,Authentication auth){
        AppUser actor=user(auth); requireManager(actor); ownedProject(id,actor); validateMeasure(in); Measurement m=measurement(id,kid,mid); m.value=in.value(); m.periodDate=in.periodDate(); m.notes=in.notes(); m.createdAt=Instant.now(); m.author=actor; m=measurements.save(m); audits.save(new MeasurementAudit(m,"UPDATED")); return measurementView(m);
    }
    @GetMapping("/projects/{id}/kpis/{kid}/measurements/{mid}/audit") List<Map<String,Object>> audit(@PathVariable Long id,@PathVariable Long kid,@PathVariable Long mid,Authentication auth){ visibleProject(id,user(auth)); measurement(id,kid,mid); return audits.findByMeasurementIdOrderByChangedAtDesc(mid).stream().map(a->Map.<String,Object>of("action",a.action,"value",a.value,"periodDate",a.periodDate,"changedAt",a.changedAt,"authorId",a.authorId)).toList(); }
    @GetMapping("/projects/{id}/kpis/{kid}/measurements/{mid}/evidence/{eid}") ResponseEntity<Resource> downloadEvidence(@PathVariable Long id,@PathVariable Long kid,@PathVariable Long mid,@PathVariable Long eid,Authentication auth) throws IOException {
        visibleProject(id,user(auth)); measurement(id,kid,mid); Evidence e=evidences.findByIdAndMeasurement_Id(eid,mid).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        org.springframework.core.io.Resource resource=new org.springframework.core.io.UrlResource(uploadPath.resolve(e.storedName).toUri());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(e.contentType)).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(e.originalName).build().toString()).body(resource);
    }
    @GetMapping("/projects/{id}/report.pdf") void report(@PathVariable Long id,Authentication auth,HttpServletResponse response) throws IOException {
        Project p=visibleProject(id,user(auth)); response.setContentType("application/pdf"); response.setHeader(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=rapport-impact-"+p.id+".pdf");
        Document doc=new Document(PageSize.A4,42,42,46,42);
        try { PdfWriter.getInstance(doc,response.getOutputStream()); doc.open();
            Font title=FontFactory.getFont(FontFactory.HELVETICA_BOLD,20,new java.awt.Color(29,78,75)); Font heading=FontFactory.getFont(FontFactory.HELVETICA_BOLD,13); Font body=FontFactory.getFont(FontFactory.HELVETICA,10);
            doc.add(new Paragraph("IMPACT BOARD",title)); doc.add(new Paragraph("Rapport d'impact",heading)); doc.add(new Paragraph(p.name,FontFactory.getFont(FontFactory.HELVETICA_BOLD,16))); doc.add(new Paragraph(Objects.toString(p.description,""),body));
            doc.add(new Paragraph("Période : "+p.startDate+" – "+p.endDate+"    Statut : "+p.status,body)); doc.add(Chunk.NEWLINE);
            for(Kpi k:kpis.findByProject_IdOrderById(p.id)){ List<Measurement> ms=measurements.findByKpi_IdOrderByPeriodDate(k.id); double total=ms.stream().mapToDouble(m->m.value).sum();
                doc.add(new Paragraph(k.name+" — "+total+" / "+k.target+" "+k.unit+" ("+Math.round(k.target==0?0:100*total/k.target)+"%)",heading));
                if(k.description!=null) doc.add(new Paragraph(k.description,body));
                PdfPTable table=new PdfPTable(new float[]{2,2,4}); table.setWidthPercentage(100); table.setSpacingBefore(6); table.setSpacingAfter(12);
                for(String h:List.of("Période","Valeur","Notes / preuves")){ PdfPCell c=new PdfPCell(new Phrase(h,FontFactory.getFont(FontFactory.HELVETICA_BOLD,9,java.awt.Color.WHITE))); c.setBackgroundColor(new java.awt.Color(29,78,75)); c.setPadding(7); table.addCell(c); }
                for(Measurement m:ms){ table.addCell(new Phrase(m.periodDate.toString(),body)); table.addCell(new Phrase(m.value+" "+k.unit,body)); String proof=m.evidence.stream().map(e->e.originalName).reduce((a,b)->a+", "+b).orElse("—"); table.addCell(new Phrase(Objects.toString(m.notes,"—")+" | "+proof,body)); }
                doc.add(table);
            }
            doc.add(new Paragraph("Généré le "+LocalDate.now()+" • Impact Board",body));
        } catch(DocumentException ex){ throw new IOException(ex); } finally { if(doc.isOpen()) doc.close(); }
    }

    private AppUser user(Authentication a){ return users.findByUsername(a.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED)); }
    private UserView userView(AppUser u){ return new UserView(u.id,u.username,u.displayName,u.role.name()); }
    private void requireManager(AppUser u){ if(u.role!=Role.MANAGER&&u.role!=Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Accès en lecture seule."); }
    private void requireAdmin(AppUser u){ if(u.role!=Role.ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Accès administrateur requis."); }
    private Project ownedProject(Long id,AppUser u){ if(u.role==Role.ADMIN) return projects.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)); return projects.findByIdAndOwner_Id(id,u.id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private Project visibleProject(Long id,AppUser u){ if(u.role==Role.ADMIN) return projects.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)); return projects.findDistinctByOwner_IdOrReaders_IdOrderByIdDesc(u.id,u.id).stream().filter(p->p.id.equals(id)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private Kpi kpi(Long pid,Long kid){ return kpis.findByIdAndProject_Id(kid,pid).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private Measurement measurement(Long pid,Long kid,Long mid){ kpi(pid,kid); return measurements.findByIdAndKpi_Id(mid,kid).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private Map<String,Object> projectView(Project p){ return Map.of("id",p.id,"name",p.name,"description",Objects.toString(p.description,""),"startDate",p.startDate,"endDate",p.endDate,"status",p.status,"owner",p.owner.displayName,"readers",p.readers.stream().map(u->u.username).toList(),"kpiCount",kpis.findByProject_IdOrderById(p.id).size()); }
    private Map<String,Object> kpiView(Kpi k){ return Map.of("id",k.id,"name",k.name,"description",Objects.toString(k.description,""),"unit",k.unit,"target",k.target,"frequency",k.frequency,"evidenceRequired",k.evidenceRequired); }
    private Map<String,Object> measurementView(Measurement m){ return Map.of("id",m.id,"periodDate",m.periodDate,"value",m.value,"notes",Objects.toString(m.notes,""),"createdAt",m.createdAt,"author",m.author.displayName,"evidence",m.evidence.stream().map(e->Map.<String,Object>of("id",e.id,"name",e.originalName,"size",e.size,"contentType",e.contentType)).toList()); }
    private void validateProject(ProjectInput i){ if(i.name()==null||i.name().isBlank()||i.startDate()==null||i.endDate()==null||i.endDate().isBefore(i.startDate())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nom et période de projet invalides."); }
    private void setReaders(Project p,List<String> names){ p.readers.clear(); if(names==null)return; for(String name:names){ if(name==null||name.isBlank())continue; AppUser reader=users.findByUsername(name.trim()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Compte partenaire introuvable : "+name)); if(reader.role!=Role.PARTNER)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Seuls les comptes partenaires peuvent être invités en lecture."); p.readers.add(reader); } }
    private void validateKpi(KpiInput i){ if(i.name()==null||i.name().isBlank()||i.unit()==null||i.unit().isBlank()||i.target()<=0||i.frequency()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Les champs nom, unité, objectif et fréquence sont obligatoires."); }
    private void applyKpi(Kpi k,KpiInput i){ k.name=i.name().trim(); k.description=i.description(); k.unit=i.unit().trim(); k.target=i.target(); k.frequency=i.frequency(); k.evidenceRequired=i.evidenceRequired(); }
    private void validateMeasure(MeasureInput i){ if(i.periodDate()==null||i.value()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Date invalide ou valeur négative."); }
    private void validateFiles(List<MultipartFile> fs) throws IOException { for(MultipartFile f:fs){ if(f.getSize()>5L*1024*1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Chaque preuve est limitée à 5 Mo."); String name=Objects.toString(f.getOriginalFilename(),"").toLowerCase(Locale.ROOT); String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1):""; String type=Objects.toString(f.getContentType(),""); byte[] header=f.getBytes(); boolean signature=switch(ext){case "pdf"->starts(header,0x25,0x50,0x44,0x46,0x2d); case "jpg","jpeg"->starts(header,0xff,0xd8,0xff); case "png"->starts(header,0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a); default->false;}; boolean mime=switch(ext){case "pdf"->type.equals("application/pdf")||type.equals("application/octet-stream"); case "jpg","jpeg"->type.equals("image/jpeg")||type.equals("application/octet-stream"); case "png"->type.equals("image/png")||type.equals("application/octet-stream"); default->false;}; if(!signature||!mime) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Formats autorisés : PDF, JPG, JPEG et PNG."); } }
    private boolean starts(byte[] bytes,int... expected){ if(bytes.length<expected.length)return false; for(int i=0;i<expected.length;i++)if((bytes[i]&0xff)!=expected[i])return false; return true; }
    private String contentType(MultipartFile f){ String n=Objects.toString(f.getOriginalFilename(),"").toLowerCase(Locale.ROOT); return n.endsWith(".pdf")?"application/pdf":n.endsWith(".png")?"image/png":"image/jpeg"; }
}
