install.packages("arrow","sf", "rgeos")

library(tidyverse)
library(lubridate)
library(sf)
library(rgeos)
library(arrow)
library(archive)
library(tmap)

output_path <- "output/path"

nyc_zones_manhattan <- read.csv("https://d37ci6vzurychx.cloudfront.net/misc/taxi+_zone_lookup.csv") %>% 
  filter(Borough == "Manhattan")


download.file(url = "https://d37ci6vzurychx.cloudfront.net/trip-data/yellow_tripdata_2022-08.parquet",
              destfile="yellow_tripdata_2022-08.parquet",
              mode = "wb")
trips_short <- read_parquet('yellow_tripdata_2022-08.parquet',as_data_frame = TRUE) %>% 
  mutate(tpep_pickup_datetime = as_datetime(tpep_pickup_datetime))%>%
  filter(mday(tpep_pickup_datetime) == 16) %>% 
  filter(PULocationID %in% nyc_zones_manhattan$LocationID) %>%
  filter(DOLocationID %in% nyc_zones_manhattan$LocationID) %>%
  select(tpep_pickup_datetime,PULocationID,DOLocationID)

download.file(url = "https://d37ci6vzurychx.cloudfront.net/misc/taxi_zones.zip",
              destfile="taxi_zones.zip",
              mode = "wb")
archive_extract("taxi_zones.zip")
nyc_shape_manhattan <- st_read("taxi_zones.shp") %>%  filter(borough == "Manhattan")


trips_short <- add_column(trips_short, PULocationCoord_x = NA, PULocationCoord_y = NA,DOLocationCoord_x = NA, DOLocationCoord_y = NA)

get_sample_from_df <- function(x) {
  st_sample(nyc_shape_manhattan[
    which(nyc_shape_manhattan$LocationID == as.numeric(x)),3], 1,1) 
}

for(i in 1:nrow(trips_short)){
  print(i)
  PU_coord <- st_coordinates(get_sample_from_df(trips_short[i,2]))
  trips_short[i,4] <- PU_coord[1]
  trips_short[i,5] <- PU_coord[2]
  DO_coord <- st_coordinates(get_sample_from_df(trips_short[i,3]))
  trips_short[i,6] <- DO_coord[1]
  trips_short[i,7] <- DO_coord[2]
}

write.csv2(trips_short, output_path, row.names=FALSE)