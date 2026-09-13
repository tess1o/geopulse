package org.github.tess1o.geopulse.digest.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.github.tess1o.geopulse.digest.model.ActivityChartData;
import org.github.tess1o.geopulse.digest.model.DigestMetrics;
import org.github.tess1o.geopulse.digest.model.PeriodInfo;
import org.github.tess1o.geopulse.digest.model.TimeDigest;
import org.github.tess1o.geopulse.immich.model.ImmichConfigResponse;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoDto;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchResponse;
import org.github.tess1o.geopulse.immich.service.ImmichService;
import org.github.tess1o.geopulse.statistics.model.BarChartData;
import org.github.tess1o.geopulse.statistics.model.TopPlace;
import org.github.tess1o.geopulse.user.model.DistanceUnit;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class DigestPdfServiceTest {
    @Test
    void generatesReadableUnicodeReport() throws Exception {
        TimeDigest digest = TimeDigest.builder()
                .period(PeriodInfo.builder().year(2026).month(8).displayName("August 2026").type("monthly").build())
                .metrics(DigestMetrics.builder().totalDistance(617_000).tripCount(123).activeDays(30).citiesVisited(4).carDistance(574_000).walkDistance(42_000).build())
                .topPlaces(List.of(TopPlace.builder().name("Парк Шевченка").visits(7).build()))
                .activityChart(ActivityChartData.builder().chartsByTripType(Map.of(
                        "CAR", new BarChartData(new String[]{"Week 1", "Week 2"}, new double[]{100, 200}),
                        "WALK", new BarChartData(new String[]{"Week 1", "Week 2"}, new double[]{4, 12})
                )).build())
                .build();
        UserEntity user = UserEntity.builder().id(UUID.randomUUID()).timezone("Europe/Kyiv").distanceUnit(DistanceUnit.KILOMETERS).build();

        byte[] report = new DigestPdfService().generate(digest, user, false);

        assertThat(report).startsWith("%PDF".getBytes());
        try (var pdf = Loader.loadPDF(report)) {
            assertThat(pdf.getNumberOfPages()).isPositive();
            assertThat(new PDFTextStripper().getText(pdf)).contains("August 2026", "Парк Шевченка", "617 km", "Drive", "Walk", "200 km");
        }
    }

    @Test
    void embedsImmichPreviewImages() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder().id(userId).timezone("Europe/Kyiv").distanceUnit(DistanceUnit.KILOMETERS).build();
        TimeDigest digest = TimeDigest.builder()
                .period(PeriodInfo.builder().year(2025).month(7).displayName("July 2025").type("monthly").build())
                .metrics(DigestMetrics.builder().totalDistance(1_000).build())
                .build();
        ImmichService immich = mock(ImmichService.class);
        when(immich.getUserImmichConfig(userId)).thenReturn(Optional.of(ImmichConfigResponse.builder().enabled(true).build()));
        when(immich.searchPhotos(eq(userId), any())).thenReturn(CompletableFuture.completedFuture(ImmichPhotoSearchResponse.builder()
                .photos(List.of(ImmichPhotoDto.builder().id("photo-1").build())).build()));
        when(immich.getPhotoPreview(userId, "photo-1")).thenReturn(CompletableFuture.completedFuture(jpegBytes()));

        DigestPdfService service = new DigestPdfService();
        service.immichService = immich;
        try (var pdf = Loader.loadPDF(service.generate(digest, user, true))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            assertThat(pdf.getPage(1).getResources().getXObjectNames()).isNotEmpty();
        }
        verify(immich).getPhotoPreview(userId, "photo-1");
        ArgumentCaptor<org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchRequest> requestCaptor = ArgumentCaptor.forClass(org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchRequest.class);
        verify(immich).searchPhotos(eq(userId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getLimit()).isEqualTo(250);
    }

    @Test
    void selectsOnePhotoPerDayAcrossThePeriod() {
        List<ImmichPhotoDto> photos = List.of(
                photo("latest", "2025-07-30T18:00:00+03:00"), photo("duplicate", "2025-07-30T12:00:00+03:00"),
                photo("day-28", "2025-07-28T12:00:00+03:00"), photo("day-24", "2025-07-24T12:00:00+03:00"),
                photo("day-19", "2025-07-19T12:00:00+03:00"), photo("day-12", "2025-07-12T12:00:00+03:00"),
                photo("day-06", "2025-07-06T12:00:00+03:00"), photo("oldest", "2025-07-01T12:00:00+03:00")
        );

        List<ImmichPhotoDto> memories = DigestPdfService.selectMemories(photos);

        assertThat(memories).hasSize(6).extracting(ImmichPhotoDto::getId)
                .containsExactly("oldest", "day-06", "day-12", "day-24", "day-28", "latest")
                .doesNotContain("duplicate");
    }

    @Test
    void favorsRatedPhotosFromTheSameDay() {
        ImmichPhotoDto ordinary = ImmichPhotoDto.builder().id("ordinary").takenAt(OffsetDateTime.parse("2025-07-10T12:00:00+03:00")).width(1600).height(1200).build();
        ImmichPhotoDto favorite = ImmichPhotoDto.builder().id("favorite").takenAt(OffsetDateTime.parse("2025-07-10T18:00:00+03:00")).width(1600).height(1200).isFavorite(true).rating(3).build();

        assertThat(DigestPdfService.selectMemories(List.of(ordinary, favorite))).extracting(ImmichPhotoDto::getId).containsExactly("favorite");
    }

    @Test
    void rejectsPhotosWithUnsuitableDimensions() {
        ImmichPhotoDto portrait = ImmichPhotoDto.builder().id("portrait").takenAt(OffsetDateTime.parse("2025-07-10T12:00:00+03:00")).width(300).height(200).isFavorite(true).build();
        ImmichPhotoDto landscape = ImmichPhotoDto.builder().id("landscape").takenAt(OffsetDateTime.parse("2025-07-10T18:00:00+03:00")).width(1600).height(1200).build();

        assertThat(DigestPdfService.selectMemories(List.of(portrait, landscape))).extracting(ImmichPhotoDto::getId).containsExactly("landscape");
    }

    private ImmichPhotoDto photo(String id, String takenAt) {
        return ImmichPhotoDto.builder().id(id).takenAt(OffsetDateTime.parse(takenAt)).build();
    }

    private byte[] jpegBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x2563eb);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpeg", output);
            return output.toByteArray();
        }
    }
}
