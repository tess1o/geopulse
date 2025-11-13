alter table shared_link
    add column history_hours int default 24;

update shared_link set history_hours = 24;