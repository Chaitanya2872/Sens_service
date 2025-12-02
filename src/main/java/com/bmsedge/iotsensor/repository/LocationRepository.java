package com.bmsedge.iotsensor.repository;

import com.bmsedge.iotsensor.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByType(String type);

    List<Location> findByFloor(Integer floor);

    List<Location> findByFloorAndZone(Integer floor, String zone);

    List<Location> findByBuilding(String building);

    List<Location> findByActive(Boolean active);

    Optional<Location> findByNameAndFloor(String name, Integer floor);

    @Query("SELECT l FROM Location l WHERE l.type = :type AND l.floor = :floor")
    List<Location> findByTypeAndFloor(@Param("type") String type, @Param("floor") Integer floor);

    @Query("SELECT DISTINCT l.floor FROM Location l WHERE l.active = true ORDER BY l.floor")
    List<Integer> findAllActiveFloors();

    @Query("SELECT l FROM Location l WHERE l.type = :type AND l.active = true")
    List<Location> findActiveByType(@Param("type") String type);
}