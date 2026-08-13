package com.g93.be.service;

import com.g93.be.entity.KnowledgeDocument;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.repository.KnowledgeDocumentRepository;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportKnowledgeSyncServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private KnowledgeDocumentRepository repository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenTextSplitter splitter;
    @Mock
    private VectorStore vectorStore;

    @Test
    void scheduledSyncRetriesIncompleteReportsAndRepairsOwnerChanges() {
        ReportKnowledgeSyncService service = new ReportKnowledgeSyncService(
                jdbcTemplate, repository, userRepository, splitter, vectorStore);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.anyString(), eq(Map.of()), eq(Long.class)))
                .thenReturn(Collections.emptyList());

        service.syncNewReports();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), any(Map.class), eq(Long.class));
        assertTrue(sql.getValue().contains("k.status <> 'INDEXED'"));
        assertTrue(sql.getValue().contains("COALESCE(k.checksum, '') <> 'report-metadata-v2'"));
        assertTrue(sql.getValue().contains("LIMIT 100"));
    }

    @Test
    void assignedDoctorCanSyncReportCreatedByAnotherClinicalUser() throws Exception {
        ReportKnowledgeSyncService service = new ReportKnowledgeSyncService(
                jdbcTemplate, repository, userRepository, splitter, vectorStore);
        User assignedDoctor = user(7L, "DOCTOR");
        User reportCreator = user(8L, "HEAD_OF_DEPARTMENT");
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(assignedDoctor));
        when(userRepository.findById(8L)).thenReturn(Optional.of(reportCreator));

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("report_id")).thenReturn(31L);
        when(resultSet.getLong("examination_id")).thenReturn(21L);
        when(resultSet.getLong("owner_user_id")).thenReturn(8L);
        when(resultSet.getObject("assigned_doctor_user_id", Long.class)).thenReturn(7L);
        when(resultSet.getObject("study_date")).thenReturn(LocalDate.of(2026, 8, 13));
        when(resultSet.getString("final_diagnosis")).thenReturn("Knee osteoarthritis");
        when(resultSet.getString("confirmed_grades")).thenReturn("3");
        when(resultSet.getString("clinical_summary")).thenReturn("Confirmed OA");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(invocation.<RowMapper<?>>getArgument(2).mapRow(resultSet, 0)));

        when(repository.findBySourceKey("report:31")).thenReturn(Optional.empty());
        when(repository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(99L);
            return document;
        });
        when(splitter.apply(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncReport(31L, "doctor");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> chunks = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(chunks.capture());
        Map<String, Object> metadata = chunks.getValue().getFirst().getMetadata();
        assertEquals(8L, metadata.get("ownerUserId"));
        assertEquals(7L, metadata.get("assignedDoctorUserId"));
        ArgumentCaptor<KnowledgeDocument> knowledge = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(knowledge.capture());
        assertEquals("report-metadata-v2", knowledge.getValue().getChecksum());
    }

    private User user(Long id, String roleCode) {
        Role role = new Role();
        role.setCode(roleCode);
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
