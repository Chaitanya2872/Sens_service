package com.bmsedge.iotsensor.repository;

import com.bmsedge.iotsensor.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantCode(String tenantCode);

    List<Tenant> findByActive(Boolean active);

    @Query("SELECT t FROM Tenant t WHERE t.active = true ORDER BY t.tenantName")
    List<Tenant> findAllActiveTenants();

    @Query("SELECT t FROM Tenant t WHERE t.city = :city AND t.active = true")
    List<Tenant> findByCity(@Param("city") String city);

    @Query("SELECT t FROM Tenant t WHERE t.organizationName = :orgName AND t.active = true")
    List<Tenant> findByOrganization(@Param("orgName") String organizationName);
}