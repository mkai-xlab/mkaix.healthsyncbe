package com.g93.be.repository;


import com.g93.be.entity.KneePathologyDetail;
import com.g93.be.entity.KneePathologyDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KneePathologyDetailRepository extends JpaRepository<KneePathologyDetail, Long> {
}
