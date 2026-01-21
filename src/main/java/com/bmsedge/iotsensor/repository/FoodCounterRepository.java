package com.bmsedge.iotsensor.repository;

import com.bmsedge.iotsensor.model.CafeteriaLocation;
import com.bmsedge.iotsensor.model.FoodCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodCounterRepository extends JpaRepository<FoodCounter, Long> {

    Optional<FoodCounter> findByDeviceId(String deviceId);

    Optional<FoodCounter> findByCounterCode(String counterCode);

    List<FoodCounter> findByCafeteriaLocationId(Long cafeteriaLocationId);

    List<FoodCounter> findByActive(Boolean active);

    @Query("SELECT f FROM FoodCounter f WHERE f.cafeteriaLocation.id = :cafeteriaLocationId AND f.active = true ORDER BY f.counterName")
    List<FoodCounter> findActiveByCafeteriaLocationId(@Param("cafeteriaLocationId") Long cafeteriaLocationId);

    @Query("SELECT f FROM FoodCounter f WHERE f.cafeteriaLocation.cafeteriaCode = :cafeteriaCode AND f.active = true")
    List<FoodCounter> findActiveByCafeteriaCode(@Param("cafeteriaCode") String cafeteriaCode);

    @Query("SELECT DISTINCT f.counterName FROM FoodCounter f WHERE f.cafeteriaLocation.id = :cafeteriaLocationId AND f.active = true")
    List<String> findAllCounterNamesByCafeteriaLocation(@Param("cafeteriaLocationId") Long cafeteriaLocationId);

    /**
     * ✅ NEW QUERY: Find counter by name and cafeteria location
     */
    @Query("SELECT fc FROM FoodCounter fc " +
            "WHERE fc.counterName = :counterName " +
            "AND fc.cafeteriaLocation = :location")
    Optional<FoodCounter> findByCounterNameAndCafeteriaLocation(
            @Param("counterName") String counterName,
            @Param("location") CafeteriaLocation location
    );



    /**
     * ✅ NEW QUERY: Find all counters by cafeteria location
     */
    @Query("SELECT fc FROM FoodCounter fc " +
            "WHERE fc.cafeteriaLocation = :location " +
            "ORDER BY fc.counterName")
    List<FoodCounter> findByCafeteriaLocation(@Param("location") CafeteriaLocation location);















}