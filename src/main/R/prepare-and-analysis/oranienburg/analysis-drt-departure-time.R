library(tidyverse)
library(lubridate)

raw_data <- read_delim("/Users/luchengqi/Documents/GitHub/drt-scenario-library/scenarios/output/oranienburg/latest-run/ITERS/it.0/0.drt_legs_drt.csv", delim = ";") %>%
  mutate(departureTime_hms = seconds_to_period(departureTime))

drt_trips <- raw_data %>%
  select(c(departureTime,fromLinkId,toLinkId))


egress <- drt_trips %>%
  filter(fromLinkId == "5405906940001r") %>%
  mutate(trip_mode = "Egress DRT trips")

access <- drt_trips %>%
  filter(toLinkId == "5405906940001r") %>%
  mutate(trip_mode = "Access DRT trips")

other_trips <- drt_trips %>%
  filter(fromLinkId != "5405906940001r" & toLinkId != "5405906940001r") %>%
  mutate(trip_mode = "Local trips")

drt_trips <- rbind(egress,access,other_trips)


ggplot(drt_trips) + 
  geom_histogram(aes(x = departureTime/3600, fill = trip_mode), binwidth = 1/12) + 
  ggtitle("Temporal distribution of DRT demands (departure time)") +
  theme(plot.title = element_text(hjust = 0.5)) + 
  #xlim(0,24) +
  xlab("Time of the day (hours)") +
  ylab("Number of departures (5-min time bin)") + 
  scale_fill_discrete(name = "Types of DRT Demand") +
  theme(axis.text = element_text(size = 18))+
  theme(axis.title = element_text(size = 22))+
  theme(legend.text = element_text(size = 20)) +
  theme(legend.title = element_text(size = 20)) +
  theme(plot.title = element_text(size = 24))  

ggplot(egress) +
  geom_histogram(aes(departureTime/3600), binwidth = 1/12)

ggplot(access) +
  geom_histogram(aes(departureTime/3600), binwidth = 1/12)

ggplot(other_trips) +
  geom_histogram(aes(departureTime/3600), binwidth = 1/12)

aggregated_total_trips <- drt_trips %>%
  group_by(departureTime)
