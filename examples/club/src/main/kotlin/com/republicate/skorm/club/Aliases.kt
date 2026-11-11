package com.republicate.skorm.club

// The bookshelf's aliases come with its module; the club aliases its own.

typealias Member = ClubDatabase.ClubSchema.Member
typealias Review = ClubDatabase.ClubSchema.Review

// the writing half

typealias MutableMember = MutableClubDatabase.MutableClubSchema.MutableMember
typealias MutableReview = MutableClubDatabase.MutableClubSchema.MutableReview
