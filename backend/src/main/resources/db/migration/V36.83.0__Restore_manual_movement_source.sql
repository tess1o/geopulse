UPDATE timeline_trips trip
SET movement_type_source = 'MANUAL'
WHERE EXISTS (
    SELECT 1 FROM timeline_edit_applications application
    JOIN timeline_edits edit ON edit.id = application.edit_id
    WHERE application.target_type = 'TRIP' AND application.target_id = trip.id
      AND edit.user_id = trip.user_id AND edit.status = 'ACTIVE' AND edit.kind IN ('SET_MOVEMENT_TYPE', 'FORCE_TRIP')
);
