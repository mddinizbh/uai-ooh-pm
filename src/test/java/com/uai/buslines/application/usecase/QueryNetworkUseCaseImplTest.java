package com.uai.buslines.application.usecase;

import com.uai.buslines.domain.model.*;
import com.uai.buslines.domain.port.out.NetworkQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link QueryNetworkUseCaseImpl}.
 *
 * <p>Uses Mockito manually (no Spring context) to keep tests fast.
 * Each test verifies one aspect of the use case delegation / exception logic.
 */
class QueryNetworkUseCaseImplTest {

    private NetworkQueryRepository queryRepo;
    private QueryNetworkUseCaseImpl useCase;

    private static final long VERSION = 42L;

    @BeforeEach
    void setUp() {
        queryRepo = mock(NetworkQueryRepository.class);
        useCase   = new QueryNetworkUseCaseImpl(queryRepo);
    }

    // ── listNeighborhoods ──────────────────────────────────────────────────────

    @Test
    void listNeighborhoods_noActiveVersion_returnsEmpty() {
        when(queryRepo.activeVersion()).thenReturn(0L);

        List<NeighborhoodSummary> result = useCase.listNeighborhoods();

        assertThat(result).isEmpty();
        verify(queryRepo, never()).listNeighborhoods(anyLong());
    }

    @Test
    void listNeighborhoods_withActiveVersion_delegatesToRepo() {
        NeighborhoodSummary summary = new NeighborhoodSummary(1L, "Centro", 2, 1, 1);
        when(queryRepo.activeVersion()).thenReturn(VERSION);
        when(queryRepo.listNeighborhoods(VERSION)).thenReturn(List.of(summary));

        List<NeighborhoodSummary> result = useCase.listNeighborhoods();

        assertThat(result).containsExactly(summary);
    }

    // ── linesByNeighborhood ────────────────────────────────────────────────────

    @Test
    void linesByNeighborhood_noActiveVersion_throwsNotFound() {
        when(queryRepo.activeVersion()).thenReturn(0L);

        assertThatThrownBy(() -> useCase.linesByNeighborhood(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void linesByNeighborhood_unknownId_throwsNotFound() {
        when(queryRepo.activeVersion()).thenReturn(VERSION);
        when(queryRepo.linesByNeighborhood(99L, VERSION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.linesByNeighborhood(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void linesByNeighborhood_groupsIntoThreeBuckets() {
        LineSummary l1 = new LineSummary(1L, "9400", "Bairro A - Centro");
        LineSummary l2 = new LineSummary(2L, "2010", "Centro - Bairro B");

        NeighborhoodLines expected = new NeighborhoodLines(
                10L, "Centro",
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}",
                List.of(l1, l2),   // passesThrough
                List.of(l1, l2),   // departsFrom
                List.of(l1));      // arrivesAt

        when(queryRepo.activeVersion()).thenReturn(VERSION);
        when(queryRepo.linesByNeighborhood(10L, VERSION)).thenReturn(Optional.of(expected));

        NeighborhoodLines result = useCase.linesByNeighborhood(10L);

        assertThat(result.passesThrough()).containsExactly(l1, l2);
        assertThat(result.departsFrom()).containsExactly(l1, l2);
        assertThat(result.arrivesAt()).containsExactly(l1);
    }

    // ── searchLines ────────────────────────────────────────────────────────────

    @Test
    void searchLines_noActiveVersion_returnsEmpty() {
        when(queryRepo.activeVersion()).thenReturn(0L);

        assertThat(useCase.searchLines("9400")).isEmpty();
        verify(queryRepo, never()).searchLines(anyString(), anyLong());
    }

    @Test
    void searchLines_byNumber_matchesShortName() {
        LineSummary l = new LineSummary(1L, "9400", "Bairro A - Centro");
        when(queryRepo.activeVersion()).thenReturn(VERSION);
        when(queryRepo.searchLines("9400", VERSION)).thenReturn(List.of(l));

        List<LineSummary> result = useCase.searchLines("9400");

        assertThat(result).containsExactly(l);
    }

    @Test
    void searchLines_byName_matchesLongName() {
        LineSummary l = new LineSummary(1L, "9400", "Bairro A - Savassi");
        when(queryRepo.activeVersion()).thenReturn(VERSION);
        when(queryRepo.searchLines("Savassi", VERSION)).thenReturn(List.of(l));

        List<LineSummary> result = useCase.searchLines("Savassi");

        assertThat(result).containsExactly(l);
    }

    @Test
    void searchLines_emptyQuery_delegatesBlankToRepo() {
        when(queryRepo.activeVersion()).thenReturn(VERSION);
        when(queryRepo.searchLines("", VERSION)).thenReturn(List.of());

        List<LineSummary> result = useCase.searchLines("");

        assertThat(result).isEmpty();
        verify(queryRepo).searchLines("", VERSION);
    }

    @Test
    void searchLines_noMatch_returnsEmpty() {
        when(queryRepo.activeVersion()).thenReturn(VERSION);
        when(queryRepo.searchLines("ZZZ", VERSION)).thenReturn(List.of());

        assertThat(useCase.searchLines("ZZZ")).isEmpty();
    }

    // ── lineDetail ─────────────────────────────────────────────────────────────

    @Test
    void lineDetail_noActiveVersion_throwsNotFound() {
        when(queryRepo.activeVersion()).thenReturn(0L);

        assertThatThrownBy(() -> useCase.lineDetail(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("1");
    }

    @Test
    void lineDetail_unknownId_throwsNotFound() {
        when(queryRepo.activeVersion()).thenReturn(VERSION);
        when(queryRepo.lineDetail(99L, VERSION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.lineDetail(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void lineDetail_returnsShapesAndEmptyStops() {
        LineSummary line  = new LineSummary(1L, "9400", "Bairro A - Centro");
        LineShape   shape = new LineShape(10L, 0, "{\"type\":\"LineString\",\"coordinates\":[[-43.9,−19.9]]}");
        LineDetail  detail = new LineDetail(line, List.of(shape), List.of());

        when(queryRepo.activeVersion()).thenReturn(VERSION);
        when(queryRepo.lineDetail(1L, VERSION)).thenReturn(Optional.of(detail));

        LineDetail result = useCase.lineDetail(1L);

        assertThat(result.line()).isEqualTo(line);
        assertThat(result.shapes()).containsExactly(shape);
        assertThat(result.stops()).isEmpty();
    }
}
