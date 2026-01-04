package com.shenzhewei.accont_book.repository;

import com.shenzhewei.accont_book.model.entity.Budget;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * 预算 Mapper
 */
@Mapper
public interface BudgetMapper {

    /**
     * 根据ID查询预算
     */
    @Select("SELECT id, user_id, `year_month`, amount, category, ai_suggested, create_time, update_time FROM tb_budget WHERE id = #{id}")
    Optional<Budget> findById(@Param("id") Long id);

    /**
     * 根据用户ID和年月查询预算列表
     */
    @Select("SELECT id, user_id, `year_month`, amount, category, ai_suggested, create_time, update_time FROM tb_budget " +
            "WHERE user_id = #{userId} AND `year_month` = #{yearMonth}")
    List<Budget> findByUserIdAndYearMonth(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);

    /**
     * 根据用户ID、年月和分类查询预算
     */
    @Select("SELECT id, user_id, `year_month`, amount, category, ai_suggested, create_time, update_time FROM tb_budget " +
            "WHERE user_id = #{userId} AND `year_month` = #{yearMonth} AND (category = #{category} OR (#{category} IS NULL AND category IS NULL))")
    Optional<Budget> findByUserIdAndYearMonthAndCategory(@Param("userId") Long userId, @Param("yearMonth") String yearMonth, @Param("category") String category);

    /**
     * 根据用户ID查询所有预算
     */
    @Select("SELECT id, user_id, `year_month`, amount, category, ai_suggested, create_time, update_time FROM tb_budget " +
            "WHERE user_id = #{userId} ORDER BY `year_month` DESC, category")
    List<Budget> findByUserId(@Param("userId") Long userId);

    /**
     * 插入预算
     */
    @Insert("INSERT INTO tb_budget (user_id, `year_month`, amount, category, ai_suggested) VALUES (#{userId}, #{yearMonth}, #{amount}, #{category}, #{aiSuggested})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Budget budget);

    /**
     * 更新预算金额
     */
    @Update("UPDATE tb_budget SET amount = #{amount}, ai_suggested = #{aiSuggested} WHERE id = #{id}")
    int update(Budget budget);

    /**
     * 删除预算
     */
    @Delete("DELETE FROM tb_budget WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 查询用户某月的总预算（category为null的那条）
     */
    @Select("SELECT id, user_id, `year_month`, amount, category, ai_suggested, create_time, update_time FROM tb_budget " +
            "WHERE user_id = #{userId} AND `year_month` = #{yearMonth} AND category IS NULL")
    Optional<Budget> findTotalBudget(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);
}

