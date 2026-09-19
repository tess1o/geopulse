package org.github.tess1o.geopulse.digest.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.github.tess1o.geopulse.digest.model.ActivityChartData;
import org.github.tess1o.geopulse.digest.model.DigestHighlight;
import org.github.tess1o.geopulse.digest.model.DigestMetrics;
import org.github.tess1o.geopulse.digest.model.Milestone;
import org.github.tess1o.geopulse.digest.model.TimeDigest;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoDto;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchRequest;
import org.github.tess1o.geopulse.immich.service.ImmichService;
import org.github.tess1o.geopulse.statistics.model.BarChartData;
import org.github.tess1o.geopulse.statistics.model.TopPlace;
import org.github.tess1o.geopulse.user.model.DistanceUnit;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Slf4j
public class DigestPdfService {
    private static final float PAGE_MARGIN = 42;
    private static final Color INK = new Color(30, 41, 59);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color BRAND = new Color(37, 99, 235);
    private static final Color WALK = new Color(16, 185, 129);
    private static final Color AMBER = new Color(245, 158, 11);
    private static final Color SURFACE = new Color(248, 250, 252);
    private static final int PHOTO_LIMIT = 6;
    private static final int PHOTO_CANDIDATE_LIMIT = 250;

    @Inject
    ImmichService immichService;

    public byte[] generate(TimeDigest digest, UserEntity user, boolean includePhotos) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Fonts fonts = loadFonts(document);
            List<Photo> photos = includePhotos ? loadPhotos(user, digest) : List.of();
            renderOverview(document, digest, user, photos, fonts);
            document.save(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate Rewind PDF", exception);
        }
    }

    private void renderOverview(PDDocument document, TimeDigest digest, UserEntity user, List<Photo> photos, Fonts fonts) throws Exception {
        PDPage firstPage = new PDPage(PDRectangle.A4);
        document.addPage(firstPage);
        try (PDPageContentStream stream = new PDPageContentStream(document, firstPage)) {
            float width = firstPage.getMediaBox().getWidth();
            drawHeader(stream, digest, user, fonts, width);
            drawMetricCards(stream, digest.getMetrics(), user.getDistanceUnit(), fonts, width);
            drawHighlights(stream, digest.getHighlights(), user, fonts, width);
            drawMovementMix(stream, digest.getMetrics(), user.getDistanceUnit(), fonts, width);
            drawTrend(stream, digest.getActivityChart(), user.getDistanceUnit(), fonts, width);
            drawFooter(stream, fonts, width, 1);
        }

        if (!photos.isEmpty() || !safeList(digest.getTopPlaces()).isEmpty() || !safeList(digest.getMilestones()).isEmpty()) {
            PDPage secondPage = new PDPage(PDRectangle.A4);
            document.addPage(secondPage);
            try (PDPageContentStream stream = new PDPageContentStream(document, secondPage)) {
                float width = secondPage.getMediaBox().getWidth();
                float y = secondPage.getMediaBox().getHeight() - PAGE_MARGIN;
                y = drawSectionTitle(stream, "Places and milestones", y, fonts);
                float gap = 14;
                float columnWidth = (width - PAGE_MARGIN * 2 - gap) / 2;
                float placesEnd = drawPlaces(stream, safeList(digest.getTopPlaces()), fonts, PAGE_MARGIN, y, columnWidth);
                float milestonesEnd = drawMilestones(stream, safeList(digest.getMilestones()), fonts, PAGE_MARGIN + columnWidth + gap, y, columnWidth);
                if (!photos.isEmpty()) drawPhotos(document, stream, photos, fonts, Math.min(placesEnd, milestonesEnd), width);
                drawFooter(stream, fonts, width, 2);
            }
        }
    }

    private void drawHeader(PDPageContentStream stream, TimeDigest digest, UserEntity user, Fonts fonts, float width) throws Exception {
        float pageHeight = PDRectangle.A4.getHeight();
        stream.setNonStrokingColor(BRAND);
        stream.addRect(0, pageHeight - 152, width, 152);
        stream.fill();
        text(stream, "GEOPULSE REWIND", PAGE_MARGIN, pageHeight - 58, 10, fonts.bold, new Color(219, 234, 254));
        text(stream, periodLabel(digest), PAGE_MARGIN, pageHeight - 98, 28, fonts.bold, Color.WHITE);
        String subtitle = formatDistance(digest.getMetrics().getTotalDistance(), user.getDistanceUnit()) + "  ·  "
                + digest.getMetrics().getTripCount() + " trips  ·  " + digest.getMetrics().getActiveDays() + " active days";
        text(stream, subtitle, PAGE_MARGIN, pageHeight - 123, 11, fonts.regular, new Color(219, 234, 254));
    }

    private static String periodLabel(TimeDigest digest) {
        Integer month = digest.getPeriod().getMonth();
        return month == null
                ? String.valueOf(digest.getPeriod().getYear())
                : YearMonth.of(digest.getPeriod().getYear(), month)
                        .format(DateTimeFormatter.ofPattern("MMMM yyyy"));
    }

    private void drawMetricCards(PDPageContentStream stream, DigestMetrics metrics, DistanceUnit unit, Fonts fonts, float width) throws Exception {
        float y = PDRectangle.A4.getHeight() - 190;
        String[][] values = {
                { "DISTANCE", formatDistance(metrics.getTotalDistance(), unit) }, { "TRIPS", String.valueOf(metrics.getTripCount()) },
                { "ACTIVE DAYS", String.valueOf(metrics.getActiveDays()) }, { "CITIES", String.valueOf(metrics.getCitiesVisited()) }
        };
        float gap = 10;
        float cardWidth = (width - PAGE_MARGIN * 2 - gap * 3) / 4;
        for (int i = 0; i < values.length; i++) {
            float x = PAGE_MARGIN + i * (cardWidth + gap);
            fillRoundRect(stream, x, y - 76, cardWidth, 76, 5, SURFACE);
            fillRect(stream, x, y - 4, cardWidth, 4, metricColor(i));
            drawMetricIcon(stream, i, x + 14, y - 25, metricColor(i));
            text(stream, values[i][0], x + 31, y - 21, 7, fonts.bold, MUTED);
            text(stream, values[i][1], x + 12, y - 56, 19, fonts.bold, INK);
        }
    }

    private void drawHighlights(PDPageContentStream stream, DigestHighlight highlights, UserEntity user, Fonts fonts, float width) throws Exception {
        float y = 542;
        text(stream, "Standout moments", PAGE_MARGIN, y, 14, fonts.bold, INK);
        List<String[]> items = new ArrayList<>();
        if (highlights != null && highlights.getLongestTrip() != null) items.add(new String[]{ "Longest trip", formatDistance(highlights.getLongestTrip().getDistance(), user.getDistanceUnit()) + " · " + formatDate(highlights.getLongestTrip().getDate(), user) });
        if (highlights != null && highlights.getMostVisited() != null) items.add(new String[]{ "Most visited", safe(highlights.getMostVisited().getName()) + " · " + highlights.getMostVisited().getVisits() + " visits" });
        if (highlights != null && highlights.getBusiestDay() != null) items.add(new String[]{ "Busiest day", highlights.getBusiestDay().getTrips() + " trips · " + formatDate(highlights.getBusiestDay().getDate(), user) });
        if (items.isEmpty()) {
            text(stream, "No standout moments recorded for this period.", PAGE_MARGIN, y - 28, 10, fonts.regular, MUTED);
            return;
        }
        float itemWidth = (width - PAGE_MARGIN * 2 - 20) / 3;
        for (int i = 0; i < items.size(); i++) {
            float x = PAGE_MARGIN + i * (itemWidth + 10);
            fillRoundRect(stream, x, y - 60, itemWidth, 46, 5, new Color(239, 246, 255));
            drawHighlightIcon(stream, i, x + 13, y - 31, metricColor(i));
            text(stream, items.get(i)[0], x + 28, y - 33, 8, fonts.bold, BRAND);
            text(stream, truncate(items.get(i)[1], 31), x + 12, y - 51, 9, fonts.regular, INK);
        }
    }

    private void drawMovementMix(PDPageContentStream stream, DigestMetrics metrics, DistanceUnit unit, Fonts fonts, float width) throws Exception {
        float y = 446;
        text(stream, "How you moved", PAGE_MARGIN, y, 14, fonts.bold, INK);
        List<Mode> modes = modes(metrics);
        if (modes.isEmpty()) return;
        float total = (float) modes.stream().mapToDouble(Mode::distance).sum();
        float x = PAGE_MARGIN;
        float availableWidth = width - PAGE_MARGIN * 2;
        for (Mode mode : modes) {
            float modeWidth = Math.max(3, availableWidth * ((float) mode.distance() / total));
            fillRect(stream, x, y - 27, modeWidth, 11, mode.color());
            x += modeWidth;
        }
        x = PAGE_MARGIN;
        float legendY = y - 47;
        for (Mode mode : modes) {
            String label = mode.label() + " " + formatDistance(mode.distance(), unit);
            fillRoundRect(stream, x, legendY - 5, 7, 7, 2, mode.color());
            text(stream, label, x + 11, legendY - 1, 8, fonts.regular, MUTED);
            x += fonts.regular.getStringWidth(label) / 1000 * 8 + 25;
        }
    }

    private void drawTrend(PDPageContentStream stream, ActivityChartData activityChart, DistanceUnit unit, Fonts fonts, float width) throws Exception {
        float y = 355;
        text(stream, "Distance over time", PAGE_MARGIN, y, 14, fonts.bold, INK);
        List<TrendSeries> series = trendSeries(activityChart, unit);
        if (series.isEmpty()) {
            text(stream, "No activity chart data for this period.", PAGE_MARGIN, y - 28, 10, fonts.regular, MUTED);
            return;
        }

        float legendX = PAGE_MARGIN + 138;
        for (TrendSeries item : series) {
            fillRoundRect(stream, legendX, y - 5, 7, 7, 2, item.color());
            text(stream, item.label(), legendX + 11, y - 1, 8, fonts.regular, MUTED);
            legendX += fonts.regular.getStringWidth(item.label()) / 1000 * 8 + 27;
        }

        int points = series.stream().mapToInt(item -> item.data().length).max().orElse(0);
        double max = Math.max(1, series.stream().flatMapToDouble(item -> java.util.Arrays.stream(item.data())).max().orElse(1));
        float chartX = PAGE_MARGIN + 34;
        float chartY = 112;
        float chartWidth = width - PAGE_MARGIN - chartX;
        float chartHeight = 188;
        stream.setStrokingColor(new Color(226, 232, 240));
        for (int tick = 0; tick <= 4; tick++) {
            float tickY = chartY + chartHeight * tick / 4;
            stream.moveTo(chartX, tickY);
            stream.lineTo(chartX + chartWidth, tickY);
            stream.stroke();
            textRight(stream, formatChartDistance(max * tick / 4, unit), chartX - 7, tickY - 3, 6.5f, fonts.regular, MUTED);
        }

        float slot = chartWidth / points;
        float groupWidth = Math.max(8, slot - 8);
        float barWidth = Math.max(3, groupWidth / series.size() - 2);
        String[] labels = series.get(0).labels();
        for (int i = 0; i < points; i++) {
            float groupX = chartX + i * slot + (slot - groupWidth) / 2;
            for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
                TrendSeries item = series.get(seriesIndex);
                double value = i < item.data().length ? item.data()[i] : 0;
                float barHeight = (float) (value / max * (chartHeight - 10));
                float x = groupX + seriesIndex * (barWidth + 2);
                if (value > 0) {
                    fillRoundRect(stream, x, chartY, barWidth, Math.max(2, barHeight), 2, item.color());
                    text(stream, formatChartDistance(value, unit), x, chartY + barHeight + 4, 6.5f, fonts.bold, item.color());
                }
            }
            if (i < labels.length) textCenter(stream, truncate(labels[i], 10), groupX + groupWidth / 2, chartY - 13, 7, fonts.regular, MUTED);
        }
    }

    private float drawPlaces(PDPageContentStream stream, List<TopPlace> places, Fonts fonts, float x, float y, float width) throws Exception {
        text(stream, "Top places", x, y, 13, fonts.bold, INK);
        y -= 38;
        if (places.isEmpty()) return y - 14;
        int rank = 1;
        for (TopPlace place : places.stream().limit(5).toList()) {
            fillRoundRect(stream, x, y - 28, width, 32, 4, SURFACE);
            fillRoundRect(stream, x + 10, y - 20, 14, 14, 7, new Color(219, 234, 254));
            textCenter(stream, String.valueOf(rank++), x + 17, y - 16, 7, fonts.bold, BRAND);
            text(stream, truncate(safe(place.getName()), 27), x + 32, y - 15, 9, fonts.regular, INK);
            textRight(stream, place.getVisits() + " visits", x + width - 10, y - 15, 8, fonts.regular, MUTED);
            y -= 38;
        }
        return y - 10;
    }

    private float drawMilestones(PDPageContentStream stream, List<Milestone> milestones, Fonts fonts, float x, float y, float width) throws Exception {
        if (milestones.isEmpty()) return y;
        text(stream, "Milestones", x, y, 13, fonts.bold, INK);
        y -= 39;
        for (Milestone milestone : milestones.stream().limit(4).toList()) {
            fillRoundRect(stream, x, y - 34, width, 39, 4, new Color(255, 251, 235));
            fillRoundRect(stream, x + 10, y - 20, 12, 12, 6, AMBER);
            text(stream, truncate(safe(milestone.getTitle().fallback()), 27), x + 30, y - 13, 9, fonts.bold, INK);
            text(stream, truncate(safe(milestone.getDescription().fallback()), 35), x + 30, y - 27, 7.5f, fonts.regular, MUTED);
            y -= 42;
        }
        return y - 8;
    }

    private void drawPhotos(PDDocument document, PDPageContentStream stream, List<Photo> photos, Fonts fonts, float y, float width) throws Exception {
        text(stream, "Photo moments", PAGE_MARGIN, y - 18, 13, fonts.bold, INK);
        float top = y - 34;
        float gap = 8;
        float availableWidth = width - PAGE_MARGIN * 2;
        for (PhotoRow row : justifiedPhotoRows(document, photos, availableWidth, 120, gap)) {
            float x = PAGE_MARGIN;
            float rowY = top - row.height();
            for (PhotoLayout item : row.photos()) {
                drawPhoto(document, stream, item.photo(), x, rowY, item.width(), row.height());
                x += item.width() + gap;
            }
            top = rowY - gap;
        }
    }

    private List<PhotoRow> justifiedPhotoRows(PDDocument document, List<Photo> photos, float availableWidth, float targetHeight, float gap) {
        List<PhotoAspect> photoAspects = photos.stream().map(photo -> new PhotoAspect(photo, photoAspectRatio(document, photo))).toList();
        float totalRatio = (float) photoAspects.stream().mapToDouble(PhotoAspect::aspectRatio).sum();
        int rowCount = Math.max(1, (int) Math.ceil(totalRatio * targetHeight / availableWidth));
        float targetRatio = totalRatio / rowCount;
        List<List<PhotoAspect>> groups = new ArrayList<>();
        List<PhotoAspect> row = new ArrayList<>();
        float ratio = 0;
        for (int index = 0; index < photoAspects.size(); index++) {
            PhotoAspect photo = photoAspects.get(index);
            row.add(photo);
            ratio += photo.aspectRatio();
            int remainingPhotos = photoAspects.size() - index - 1;
            int remainingRows = rowCount - groups.size() - 1;
            if (groups.size() < rowCount - 1 && ratio >= targetRatio && remainingPhotos >= remainingRows) {
                groups.add(row);
                row = new ArrayList<>();
                ratio = 0;
            }
        }
        if (!row.isEmpty()) groups.add(row);

        return groups.stream().map(group -> {
            float groupRatio = (float) group.stream().mapToDouble(PhotoAspect::aspectRatio).sum();
            float height = (availableWidth - gap * (group.size() - 1)) / groupRatio;
            return new PhotoRow(height, group.stream().map(photo -> new PhotoLayout(photo.photo(), photo.aspectRatio() * height)).toList());
        }).toList();
    }

    private float photoAspectRatio(PDDocument document, Photo photo) {
        try (ByteArrayInputStream image = new ByteArrayInputStream(photo.bytes())) {
            PDImageXObject object = JPEGFactory.createFromStream(document, image);
            return object.getHeight() > 0 ? (float) object.getWidth() / object.getHeight() : 4f / 3f;
        } catch (Exception exception) {
            return 4f / 3f;
        }
    }

    private void drawPhoto(PDDocument document, PDPageContentStream stream, Photo photo, float x, float y, float width, float height) {
        try (ByteArrayInputStream image = new ByteArrayInputStream(photo.bytes())) {
            PDImageXObject object = JPEGFactory.createFromStream(document, image);
            stream.drawImage(object, x, y, width, height);
        } catch (Exception exception) {
            log.debug("Skipping unreadable Immich preview in PDF", exception);
        }
    }

    private float drawSectionTitle(PDPageContentStream stream, String title, float y, Fonts fonts) throws Exception {
        text(stream, title, PAGE_MARGIN, y, 14, fonts.bold, INK);
        return y - 32;
    }

    private void drawFooter(PDPageContentStream stream, Fonts fonts, float width, int page) throws Exception {
        text(stream, "GeoPulse · Your private location story", PAGE_MARGIN, 30, 8, fonts.regular, MUTED);
        textRight(stream, "Page " + page, width - PAGE_MARGIN, 30, 8, fonts.regular, MUTED);
    }

    private List<Photo> loadPhotos(UserEntity user, TimeDigest digest) {
        if (immichService.getUserImmichConfig(user.getId()).filter(config -> Boolean.TRUE.equals(config.getEnabled())).isEmpty()) return List.of();
        try {
            ZoneId zone = ZoneId.of(user.getTimezone());
            Instant start = digest.getPeriod().getMonth() == null
                    ? LocalDate.of(digest.getPeriod().getYear(), 1, 1).atStartOfDay(zone).toInstant()
                    : YearMonth.of(digest.getPeriod().getYear(), digest.getPeriod().getMonth()).atDay(1).atStartOfDay(zone).toInstant();
            Instant end = digest.getPeriod().getMonth() == null
                    ? LocalDate.of(digest.getPeriod().getYear(), 12, 31).plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
                    : YearMonth.of(digest.getPeriod().getYear(), digest.getPeriod().getMonth()).atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);
            ImmichPhotoSearchRequest request = new ImmichPhotoSearchRequest();
            request.setStartDate(OffsetDateTime.ofInstant(start, zone));
            request.setEndDate(OffsetDateTime.ofInstant(end, zone));
            request.setLimit(PHOTO_CANDIDATE_LIMIT);
            List<ImmichPhotoDto> found = immichService.searchPhotos(user.getId(), request).get(15, TimeUnit.SECONDS).getPhotos();
            List<Photo> photos = new ArrayList<>();
            for (ImmichPhotoDto photo : selectMemories(found, zone)) {
                try { photos.add(new Photo(immichService.getPhotoPreview(user.getId(), photo.getId()).get(15, TimeUnit.SECONDS))); }
                catch (Exception exception) { log.debug("Skipping unavailable Immich photo {} in PDF", photo.getId()); }
            }
            return photos;
        } catch (Exception exception) {
            log.debug("Immich photos unavailable for Rewind PDF", exception);
            return List.of();
        }
    }

    static List<ImmichPhotoDto> selectMemories(List<ImmichPhotoDto> photos) {
        return selectMemories(photos, ZoneId.of("UTC"));
    }

    static List<ImmichPhotoDto> selectMemories(List<ImmichPhotoDto> photos, ZoneId zone) {
        if (photos == null || photos.isEmpty()) return List.of();
        Map<LocalDate, List<ImmichPhotoDto>> photosByDay = new java.util.TreeMap<>();
        List<ImmichPhotoDto> undated = new ArrayList<>();
        for (ImmichPhotoDto photo : photos) {
            if (photo == null || photo.getId() == null || qualityScore(photo) == Integer.MIN_VALUE) continue;
            if (photo.getTakenAt() == null) undated.add(photo);
            else photosByDay.computeIfAbsent(photo.getTakenAt().atZoneSameInstant(zone).toLocalDate(), ignored -> new ArrayList<>()).add(photo);
        }
        List<ImmichPhotoDto> dailyMemories = new ArrayList<>(photosByDay.values().stream()
                .map(dayPhotos -> dayPhotos.stream().max(Comparator.comparingInt(DigestPdfService::qualityScore)).orElseThrow())
                .toList());
        if (dailyMemories.isEmpty()) dailyMemories.addAll(undated);
        if (dailyMemories.size() <= PHOTO_LIMIT) return dailyMemories;

        List<ImmichPhotoDto> selected = new ArrayList<>();
        for (int index = 0; index < PHOTO_LIMIT; index++) {
            int sourceIndex = Math.round(index * (dailyMemories.size() - 1f) / (PHOTO_LIMIT - 1));
            selected.add(dailyMemories.get(sourceIndex));
        }
        return selected;
    }

    private static int qualityScore(ImmichPhotoDto photo) {
        int width = photo.getWidth() == null ? 0 : photo.getWidth();
        int height = photo.getHeight() == null ? 0 : photo.getHeight();
        int score = Boolean.TRUE.equals(photo.getIsFavorite()) ? 100 : 0;
        score += Math.max(0, photo.getRating() == null ? 0 : photo.getRating()) * 20;
        if (width <= 0 || height <= 0) return score;

        double ratio = (double) width / height;
        if (Math.min(width, height) < 480 || ratio < 0.45 || ratio > 2.25) return Integer.MIN_VALUE;
        score += 5;
        return score;
    }

    private Fonts loadFonts(PDDocument document) {
        try (InputStream regular = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf"); InputStream bold = getClass().getResourceAsStream("/fonts/NotoSans-SemiBold.ttf")) {
            if (regular != null && bold != null) return new Fonts(PDType0Font.load(document, regular, true), PDType0Font.load(document, bold, true));
        } catch (Exception exception) { log.warn("Could not load bundled PDF font, falling back to Helvetica", exception); }
        return new Fonts(new PDType1Font(Standard14Fonts.FontName.HELVETICA), new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
    }

    private List<Mode> modes(DigestMetrics metrics) {
        return List.of(new Mode("Car", metrics.getCarDistance(), BRAND), new Mode("Walk", metrics.getWalkDistance(), WALK), new Mode("Bicycle", metrics.getBicycleDistance(), AMBER), new Mode("Running", metrics.getRunningDistance(), new Color(139, 92, 246)), new Mode("Train", metrics.getTrainDistance(), new Color(100, 116, 139)), new Mode("Flight", metrics.getFlightDistance(), new Color(239, 68, 68))).stream().filter(mode -> mode.distance() > 0).toList();
    }

    private List<TrendSeries> trendSeries(ActivityChartData chart, DistanceUnit unit) {
        if (chart == null || chart.getChartsByTripType() == null) return List.of();
        List<TrendSeries> result = new ArrayList<>();
        addTrendSeries(result, chart.getChartsByTripType(), "CAR", "Drive", BRAND, unit);
        addTrendSeries(result, chart.getChartsByTripType(), "WALK", "Walk", WALK, unit);
        if (!result.isEmpty()) return result;
        chart.getChartsByTripType().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().getData() != null && entry.getValue().getData().length > 0)
                .sorted(Map.Entry.comparingByKey())
                .limit(2)
                .forEach(entry -> addTrendSeries(result, chart.getChartsByTripType(), entry.getKey(), titleCase(entry.getKey()), BRAND, unit));
        return result;
    }

    private void addTrendSeries(List<TrendSeries> target, Map<String, BarChartData> charts, String key, String label, Color color, DistanceUnit unit) {
        BarChartData chart = charts.get(key);
        if (chart == null || chart.getData() == null || chart.getData().length == 0) return;
        double[] values = java.util.Arrays.stream(chart.getData()).map(value -> unit == DistanceUnit.MILES ? value * 0.621371 : value).toArray();
        if (java.util.Arrays.stream(values).anyMatch(value -> value > 0)) target.add(new TrendSeries(label, chart.getLabels() == null ? new String[0] : chart.getLabels(), values, color));
    }

    private String formatDistance(double meters, DistanceUnit unit) {
        double value = unit == DistanceUnit.MILES ? meters / 1609.344 : meters / 1000d;
        return String.format(Locale.ROOT, value >= 100 ? "%.0f %s" : "%.1f %s", value, unit == DistanceUnit.MILES ? "mi" : "km");
    }

    private String formatChartDistance(double value, DistanceUnit unit) {
        return String.format(Locale.ROOT, value >= 100 ? "%.0f %s" : "%.1f %s", value, unit == DistanceUnit.MILES ? "mi" : "km");
    }

    private String titleCase(String value) {
        String clean = safe(value).toLowerCase(Locale.ROOT);
        return clean.isEmpty() ? clean : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private Color metricColor(int index) {
        return switch (index) {
            case 1 -> AMBER;
            case 2 -> WALK;
            case 3 -> new Color(139, 92, 246);
            default -> BRAND;
        };
    }

    private void drawMetricIcon(PDPageContentStream stream, int index, float x, float y, Color color) throws Exception {
        fillRoundRect(stream, x - 7, y - 7, 14, 14, 7, color);
        stream.setStrokingColor(Color.WHITE);
        stream.setLineWidth(1.1f);
        if (index == 0) {
            stream.moveTo(x, y - 4); stream.lineTo(x, y + 4);
            stream.moveTo(x - 4, y); stream.lineTo(x + 4, y);
        } else if (index == 1) {
            stream.addRect(x - 4, y - 1, 8, 3);
            stream.moveTo(x - 3, y - 3); stream.lineTo(x + 3, y - 3);
        } else if (index == 2) {
            stream.addRect(x - 4, y - 4, 8, 8);
            stream.moveTo(x - 4, y + 1); stream.lineTo(x + 4, y + 1);
        } else {
            stream.moveTo(x - 4, y - 4); stream.lineTo(x - 4, y + 4);
            stream.moveTo(x, y - 1); stream.lineTo(x, y + 4);
            stream.moveTo(x + 4, y - 3); stream.lineTo(x + 4, y + 4);
        }
        stream.stroke();
    }

    private void drawHighlightIcon(PDPageContentStream stream, int index, float x, float y, Color color) throws Exception {
        fillRoundRect(stream, x - 5, y - 5, 10, 10, 5, color);
        stream.setStrokingColor(Color.WHITE);
        stream.setLineWidth(1f);
        if (index == 0) {
            stream.moveTo(x - 3, y - 2); stream.lineTo(x, y + 2); stream.lineTo(x + 3, y - 2);
        } else if (index == 1) {
            stream.moveTo(x, y - 3); stream.lineTo(x, y + 3);
            stream.moveTo(x - 2, y + 1); stream.lineTo(x, y + 3);
            stream.moveTo(x + 2, y + 1); stream.lineTo(x, y + 3);
        } else {
            stream.moveTo(x + 1, y + 4); stream.lineTo(x - 2, y); stream.lineTo(x + 1, y); stream.lineTo(x - 1, y - 4);
        }
        stream.stroke();
    }
    private String formatDate(Instant instant, UserEntity user) {
        if (instant == null) return "";
        String pattern = switch (safe(user.getDateFormat()).toUpperCase(Locale.ROOT)) {
            case "DMY" -> "dd/MM/yyyy";
            case "YMD" -> "yyyy-MM-dd";
            default -> "MM/dd/yyyy";
        };
        return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.of(user.getTimezone())).format(instant);
    }
    private void fillRect(PDPageContentStream stream, float x, float y, float width, float height, Color color) throws Exception { stream.setNonStrokingColor(color); stream.addRect(x, y, width, height); stream.fill(); }
    private void fillRoundRect(PDPageContentStream stream, float x, float y, float width, float height, float radius, Color color) throws Exception {
        float r = Math.min(radius, Math.min(width, height) / 2);
        float curve = r * 0.55228475f;
        stream.setNonStrokingColor(color);
        stream.moveTo(x + r, y);
        stream.lineTo(x + width - r, y);
        stream.curveTo(x + width - r + curve, y, x + width, y + r - curve, x + width, y + r);
        stream.lineTo(x + width, y + height - r);
        stream.curveTo(x + width, y + height - r + curve, x + width - r + curve, y + height, x + width - r, y + height);
        stream.lineTo(x + r, y + height);
        stream.curveTo(x + r - curve, y + height, x, y + height - r + curve, x, y + height - r);
        stream.lineTo(x, y + r);
        stream.curveTo(x, y + r - curve, x + r - curve, y, x + r, y);
        stream.closePath();
        stream.fill();
    }
    private void text(PDPageContentStream stream, String value, float x, float y, float size, PDFont font, Color color) throws Exception { stream.beginText(); stream.setFont(font, size); stream.setNonStrokingColor(color); stream.newLineAtOffset(x, y); stream.showText(safe(value)); stream.endText(); }
    private void textRight(PDPageContentStream stream, String value, float x, float y, float size, PDFont font, Color color) throws Exception { text(stream, value, x - font.getStringWidth(safe(value)) / 1000 * size, y, size, font, color); }
    private void textCenter(PDPageContentStream stream, String value, float x, float y, float size, PDFont font, Color color) throws Exception { text(stream, value, x - font.getStringWidth(safe(value)) / 2000 * size, y, size, font, color); }
    private String safe(String value) { return value == null ? "" : value.replace('\n', ' ').replace('\r', ' '); }
    private String truncate(String value, int max) { String clean = safe(value); return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max - 1)) + "…"; }
    private <T> List<T> safeList(List<T> value) { return value == null ? List.of() : value; }
    private record Fonts(PDFont regular, PDFont bold) {}
    private record Mode(String label, double distance, Color color) {}
    private record TrendSeries(String label, String[] labels, double[] data, Color color) {}
    private record Photo(byte[] bytes) {}
    private record PhotoAspect(Photo photo, float aspectRatio) {}
    private record PhotoLayout(Photo photo, float width) {}
    private record PhotoRow(float height, List<PhotoLayout> photos) {}
}
