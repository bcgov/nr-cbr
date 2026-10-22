package ca.bc.gov.nrs.cbr.service.v1;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Fills a report template with rows and produces a PDF.
 *
 * <p>The templates are {@code classpath:reports/<NAME>.jrxml}, compiled once and kept: all of them
 * on a background thread as the application starts, as EDUC's report services do, so the first
 * user to ask for a report does not wait a few seconds for the compiler. One asked for before its
 * turn is compiled there and then.
 *
 * <p>Unlike nr-frep's {@code ReportService}, the template carries no SQL: the rows come from the
 * service that built them, as maps keyed by the template's field names, so the same rows can be
 * written as a CSV and say exactly what the PDF says.
 *
 * <p>The templates name Arial; {@code jasperreports_extension.properties} draws it as Liberation
 * Sans, which the container has because it is on the classpath.
 */
@Component
public class ReportRenderer {

  private static final Logger log = LoggerFactory.getLogger(ReportRenderer.class);

  private static final String TEMPLATES = "classpath:reports/*.jrxml";

  private final Map<String, JasperReport> compiled = new ConcurrentHashMap<>();

  /**
   * Compiles every template in the background. Off the startup thread, so the application is not
   * held from serving while it runs; a template that fails is logged and left to fail again, with
   * its cause, when it is asked for — one broken report must not take the others down.
   */
  @PostConstruct
  void compileAll() {
    Thread.ofVirtual().name("report-compile").start(this::compileTemplates);
  }

  /** Compiles every template on the classpath; the names of those that compiled. */
  List<String> compileTemplates() {
    List<String> done = new ArrayList<>();
    Resource[] templates;
    try {
      templates = new PathMatchingResourcePatternResolver().getResources(TEMPLATES);
    } catch (IOException ex) {
      log.error("Could not list the report templates to compile", ex);
      return done;
    }
    for (Resource template : templates) {
      String filename = template.getFilename();
      if (filename == null) {
        continue;
      }
      String name = filename.replaceFirst("\\.jrxml$", "");
      try {
        compiled.computeIfAbsent(name, ReportRenderer::compile);
        done.add(name);
        log.info("Compiled report template {}", name);
      } catch (RuntimeException ex) {
        log.error("Report template {} did not compile; it will fail when asked for", name, ex);
      }
    }
    return done;
  }

  /**
   * The PDF of a template filled with these rows.
   *
   * @param template   the template's name, without folder or extension
   * @param parameters the template's {@code $P{...}} values
   * @param rows       one map per detail row, keyed by the template's {@code $F{...}} names
   * @throws ReportRenderingException when the template cannot be compiled, filled or exported
   */
  public byte[] pdf(
      String template, Map<String, Object> parameters, Collection<Map<String, ?>> rows) {
    try {
      JasperPrint print = JasperFillManager.fillReport(
          compiled.computeIfAbsent(template, ReportRenderer::compile),
          new HashMap<>(parameters),
          new JRMapCollectionDataSource(rows));
      return JasperExportManager.exportReportToPdf(print);
    } catch (JRException ex) {
      throw new ReportRenderingException("Could not produce the " + template + " report", ex);
    }
  }

  /**
   * A template compiled, to hand to another as a subreport parameter — the Inspections Conducted –
   * Detail's items, repairs and monitoring items.
   */
  public JasperReport compiled(String template) {
    return compiled.computeIfAbsent(template, ReportRenderer::compile);
  }

  private static JasperReport compile(String template) {
    ClassPathResource resource = new ClassPathResource("reports/" + template + ".jrxml");
    try (InputStream jrxml = resource.getInputStream()) {
      return JasperCompileManager.compileReport(jrxml);
    } catch (IOException ex) {
      throw new UncheckedIOException("No report template " + template, ex);
    } catch (JRException ex) {
      throw new ReportRenderingException("Could not compile the " + template + " report", ex);
    }
  }

  /** A template that would not compile, fill or export — a defect, answered as a 500. */
  public static class ReportRenderingException extends RuntimeException {
    public ReportRenderingException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
