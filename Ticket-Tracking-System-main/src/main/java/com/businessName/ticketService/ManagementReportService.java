package com.businessName.ticketService;

import com.businessName.security.AuthException;
import com.businessName.ticketDao.ConnectionObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

public class ManagementReportService {
    public JSONObject build(String dateFrom, String dateTo) {
        return build(dateFrom, dateTo, null);
    }

    public JSONObject build(String dateFrom, String dateTo, String type) {
        LocalDate to = parseDate(dateTo, LocalDate.now());
        LocalDate from = parseDate(dateFrom, to.minusDays(30));
        String reportType = parseType(type);
        if (from.isAfter(to)) {
            throw new AuthException(400, "dateFrom must be before or equal to dateTo");
        }
        Map<String, TechnicianTotals> technicians = new LinkedHashMap<>();
        JSONArray cases = new JSONArray();
        TechnicianTotals team = new TechnicianTotals("TOTAL", "Equipo completo");
        String sql = "SELECT t.ticket_number, t.status, t.created_at, t.resolved_at, " +
                "COALESCE(t.due_at, sc.due_at) AS due_at, sc.type, sc.title, " +
                "COALESCE(tech.username, 'UNASSIGNED') AS technician_code, " +
                "COALESCE(NULLIF(TRIM(tech.first_name || ' ' || tech.last_name), ''), 'Sin asignar') AS technician_name " +
                "FROM p2_sandbox.ticket t " +
                "JOIN p2_sandbox.service_case sc ON sc.case_id = t.service_case_id " +
                "LEFT JOIN p2_sandbox.app_users tech ON tech.user_id = t.assigned_to_user_id " +
                "WHERE t.deleted_at IS NULL AND sc.deleted_at IS NULL " +
                "AND sc.created_at >= ? AND sc.created_at < ? " +
                "AND (? IS NULL OR sc.type = ?) ORDER BY technician_name, sc.created_at";
        try (Connection connection = ConnectionObject.createConnection()) {
            if (connection == null) {
                throw new AuthException(503, "Database is unavailable");
            }
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setDate(1, java.sql.Date.valueOf(from));
                ps.setDate(2, java.sql.Date.valueOf(to.plusDays(1)));
                ps.setString(3, reportType);
                ps.setString(4, reportType);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String code = rs.getString("technician_code");
                        TechnicianTotals totals = technicians.get(code);
                        if (totals == null) {
                            totals = new TechnicianTotals(code, rs.getString("technician_name"));
                            technicians.put(code, totals);
                        }
                        JSONObject item = caseJson(rs);
                        totals.add(item);
                        team.add(item);
                        cases.put(item);
                    }
                }
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to build management report");
        }
        JSONArray byTechnician = new JSONArray();
        for (TechnicianTotals totals : technicians.values()) {
            byTechnician.put(totals.toJson());
        }
        return new JSONObject()
                .put("dateFrom", from.toString())
                .put("dateTo", to.toString())
                .put("type", reportType == null ? JSONObject.NULL : reportType)
                .put("byTechnician", byTechnician)
                .put("teamTotals", team.toJson())
                .put("cases", cases);
    }

    public String toExcelXml(JSONObject report) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<?mso-application progid=\"Excel.Sheet\"?>")
                .append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ")
                .append("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">");
        appendSummarySheet(xml, report);
        appendCasesSheet(xml, report);
        return xml.append("</Workbook>").toString();
    }

    private void appendSummarySheet(StringBuilder xml, JSONObject report) {
        xml.append("<Worksheet ss:Name=\"Resumen\"><Table>");
        row(xml, "Tecnico", "Generados", "Pendientes", "Resueltos", "Fuera SLA", "Dentro SLA", "Promedio atencion (min)");
        JSONArray rows = report.getJSONArray("byTechnician");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.getJSONObject(i);
            row(xml, item.optString("technicianName"), item.optLong("generated"), item.optLong("pending"),
                    item.optLong("resolved"), item.optLong("outsideSla"), item.optLong("withinSla"),
                    item.optDouble("averageAttentionMinutes"));
        }
        JSONObject total = report.getJSONObject("teamTotals");
        row(xml, "TOTAL EQUIPO", total.optLong("generated"), total.optLong("pending"), total.optLong("resolved"),
                total.optLong("outsideSla"), total.optLong("withinSla"), total.optDouble("averageAttentionMinutes"));
        xml.append("</Table></Worksheet>");
    }

    private void appendCasesSheet(StringBuilder xml, JSONObject report) {
        xml.append("<Worksheet ss:Name=\"Casos\"><Table>");
        row(xml, "Ticket", "Tecnico", "Titulo", "Tipo", "Estado", "Creacion", "Resolucion", "SLA");
        JSONArray rows = report.getJSONArray("cases");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.getJSONObject(i);
            row(xml, item.optString("ticketNumber"), item.optString("technicianName"), item.optString("title"),
                    item.optString("type"), item.optString("status"), item.optString("createdAt"),
                    item.optString("resolvedAt"), item.optString("slaClassification"));
        }
        xml.append("</Table></Worksheet>");
    }

    private JSONObject caseJson(ResultSet rs) throws Exception {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        Timestamp dueAt = rs.getTimestamp("due_at");
        boolean completed = "RESOLVED".equals(rs.getString("status")) || "CLOSED".equals(rs.getString("status"));
        Timestamp slaEnd = completed && resolvedAt != null ? resolvedAt : Timestamp.from(java.time.Instant.now());
        boolean withinSla = dueAt == null || !slaEnd.after(dueAt);
        return new JSONObject()
                .put("ticketNumber", rs.getString("ticket_number"))
                .put("technicianCode", rs.getString("technician_code"))
                .put("technicianName", rs.getString("technician_name"))
                .put("title", rs.getString("title"))
                .put("type", rs.getString("type"))
                .put("status", rs.getString("status"))
                .put("createdAt", createdAt == null ? JSONObject.NULL : createdAt.toInstant().toString())
                .put("resolvedAt", resolvedAt == null ? JSONObject.NULL : resolvedAt.toInstant().toString())
                .put("attentionMinutes", resolvedAt == null || createdAt == null
                        ? JSONObject.NULL : Math.max(0, Duration.between(createdAt.toInstant(), resolvedAt.toInstant()).toMinutes()))
                .put("slaClassification", withinSla ? "WITHIN" : "OUTSIDE");
    }

    private LocalDate parseDate(String value, LocalDate defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new AuthException(400, "Invalid date format. Use YYYY-MM-DD");
        }
    }

    private String parseType(String value) {
        if (value == null || value.trim().isEmpty() || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!"REQUEST".equals(normalized) && !"INCIDENT".equals(normalized)) {
            throw new AuthException(400, "type must be REQUEST or INCIDENT");
        }
        return normalized;
    }

    private void row(StringBuilder xml, Object... values) {
        xml.append("<Row>");
        for (Object value : values) {
            boolean number = value instanceof Number;
            xml.append("<Cell><Data ss:Type=\"").append(number ? "Number" : "String").append("\">")
                    .append(escape(value)).append("</Data></Cell>");
        }
        xml.append("</Row>");
    }

    private String escape(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static class TechnicianTotals {
        private final String technicianCode;
        private final String technicianName;
        private long generated;
        private long pending;
        private long resolved;
        private long outsideSla;
        private long withinSla;
        private long attentionMinutes;
        private long attentionSamples;

        private TechnicianTotals(String technicianCode, String technicianName) {
            this.technicianCode = technicianCode;
            this.technicianName = technicianName;
        }

        private void add(JSONObject item) {
            generated++;
            String status = item.optString("status");
            if ("RESOLVED".equals(status) || "CLOSED".equals(status)) {
                resolved++;
            } else {
                pending++;
            }
            if ("OUTSIDE".equals(item.optString("slaClassification"))) {
                outsideSla++;
            } else {
                withinSla++;
            }
            if (!item.isNull("attentionMinutes")) {
                attentionMinutes += item.getLong("attentionMinutes");
                attentionSamples++;
            }
        }

        private JSONObject toJson() {
            double average = attentionSamples == 0 ? 0.0
                    : Math.round((attentionMinutes * 10.0) / attentionSamples) / 10.0;
            return new JSONObject()
                    .put("technicianCode", technicianCode)
                    .put("technicianName", technicianName)
                    .put("generated", generated)
                    .put("pending", pending)
                    .put("resolved", resolved)
                    .put("outsideSla", outsideSla)
                    .put("withinSla", withinSla)
                    .put("averageAttentionMinutes", average);
        }
    }
}
