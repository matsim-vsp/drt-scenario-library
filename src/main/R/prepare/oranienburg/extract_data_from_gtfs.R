# Author: Chengqi Lu (luchengqi7)
library(sf)
library(tmap)
library(tidytransit)
library(tidyverse)
library(lubridate)

berlin_gtfs <- read_gtfs("../../../../../../../RStudio-workspace/Data-science-course/week-11/berlin_gtfs.zip") #TODO upload the data to SVN

gtfs_filtered <- berlin_gtfs %>%
  filter_feed_by_date("2023-01-11", "00:00:00","23:59:59")
gtfs_filtered <- gtfs_as_sf(gtfs_filtered)
view(gtfs_filtered$agency) # agency_id of DB Regio is 108

## Get S-Bahn and Regio Bahn routes
routes <- gtfs_filtered$routes
# S-Bahn
routes_sbahn <- routes %>% 
  filter(agency_id == 1) %>%
  select(c("route_id", "route_short_name"))
route_ids_sbahn <- routes_sbahn$route_id
# Regio
routes_db_regio <- routes %>% 
  filter(agency_id == 108) %>%
  select(c("route_id", "route_short_name"))
route_ids_db_regio <- routes_db_regio$route_id 

## Get relevant trips
trips <- gtfs_filtered$trips

trips_sbahn <- trips %>% 
  filter(route_id %in% route_ids_sbahn) %>%
  left_join(routes_sbahn, by = "route_id") %>%
  select(c(route_id, trip_id, trip_headsign, route_short_name))
trip_ids_sbahn <- trips_sbahn$trip_id

trips_regio <- trips %>% 
  filter(route_id %in% route_ids_db_regio) %>%
  left_join(routes_db_regio, by = "route_id") %>%
  select(c(route_id, trip_id, trip_headsign, route_short_name))
trips_ids_regio <- trips_regio$trip_id

## Get stop Oranienburg (S-Bhf and Bhf)
stops <- gtfs_filtered$stops
stops_oranienburg <- stops %>% 
  filter(grepl("Oranienburg Bhf", stop_name))

## Stop times
stop_times <- gtfs_filtered$stop_times
# S-Bahn
stop_times_sbahn <- stop_times %>% 
  filter(trip_id %in% trip_ids_sbahn) %>%
  filter(stop_id %in% stops_oranienburg$stop_id) %>%
  left_join(trips_sbahn, by = "trip_id")

# Regio
stop_times_regio <- stop_times %>% 
  filter(trip_id %in% trips_ids_regio) %>%
  filter(stop_id %in% stops_oranienburg$stop_id) %>%
  left_join(trips_regio, by = "trip_id") %>%
  filter(route_short_name != "RB20")  # RB20 does not go to Berlin, which makes it irrelevant in this scenario

## Output data
data_s_bahn <- stop_times_sbahn %>%
  mutate(type = ifelse(stop_sequence == 0, "departure", "arrival")) %>%
  mutate(time = ifelse(type == "departure", departure_time, arrival_time)) %>%
  select(c(type, route_short_name, time))

data_regio <- stop_times_regio %>%
  mutate(type = ifelse(stop_sequence == 0, "departure", "unknown")) %>%
  mutate(type = ifelse(type == "unknown" & route_short_name == "RB32", "arrival", type)) %>%
  mutate(type = ifelse(type == "unknown" & (stop_id == "de:12065:900200005:2:2" | stop_id == "de:12065:900200005:2:1"), "departure", type)) %>%
  mutate(type = ifelse(type == "unknown", "arrival", type)) %>%
  mutate(time = ifelse(type == "departure", departure_time, arrival_time)) %>%
  select(c(type, route_short_name, time))

data_output <- rbind(data_regio, data_s_bahn)
write_csv(data_output, "/Users/luchengqi/Documents/MATSimScenarios/Oranienburg/timetables/train-timetables.csv")


