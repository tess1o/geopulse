-- How the traveller gets TO this stop from the previous one in the plan, used to route the line
-- between them on the map. NULL means automatic (chosen from the distance between the stops).
ALTER TABLE trip_plan_items ADD COLUMN travel_mode VARCHAR(16);
